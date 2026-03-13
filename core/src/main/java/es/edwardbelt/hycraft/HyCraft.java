package es.edwardbelt.hycraft;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.setup.ClientFeature;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.hypixel.hytale.server.core.asset.common.events.SendCommonAssetsEvent;
import com.hypixel.hytale.server.core.auth.PlayerAuthentication;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import es.edwardbelt.hycraft.api.HyCraftApi;
import es.edwardbelt.hycraft.api.connection.HyCraftConnection;
import es.edwardbelt.hycraft.api.gui.HyCraftGui;
import es.edwardbelt.hycraft.config.ConfigManager;
import es.edwardbelt.hycraft.mapping.MappingRegistry;
import es.edwardbelt.hycraft.network.MinecraftServerBootstrap;
import es.edwardbelt.hycraft.network.ResourcePackBuilder;
import es.edwardbelt.hycraft.network.handler.minecraft.manager.gui.GuiManager;
import es.edwardbelt.hycraft.network.player.ClientConnection;
import es.edwardbelt.hycraft.network.skin.SkinTranslator;
import es.edwardbelt.hycraft.util.Logger;
import lombok.Getter;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class HyCraft extends JavaPlugin implements HyCraftApi {
    public static final String PATH = "mods/HyCraft";
    private static HyCraft INSTANCE;
    public static HyCraft get() { return INSTANCE; }

    private final MinecraftServerBootstrap minecraftServerBootstrap;
    @Getter
    private final ConfigManager configManager;

    public HyCraft(@Nonnull JavaPluginInit init) {
        super(init);
        INSTANCE = this;

        this.minecraftServerBootstrap = new MinecraftServerBootstrap();
        this.configManager = new ConfigManager();
    }

    @Override
    protected void setup() {
        HyCraftApi.setInstance(this);
        configManager.reload();

        if (configManager.getMain().isSkinMcToHytale()) {
            registerSkinEvents();
        }
    }

    private void registerSkinEvents() {
        getEventRegistry().register(SendCommonAssetsEvent.class, event -> {
            if (event == null) return;
            PacketHandler handler = event.getPacketHandler();
            if (handler == null) return;
            PlayerAuthentication auth = handler.getAuth();
            if (auth == null) return;
            String username = auth.getUsername();
            if (username == null || username.isBlank()) return;

            SkinTranslator translator = SkinTranslator.get();
            if (!translator.hasSkin(username)) return;

            try {
                Path skinPath = translator.getSkinPath(username);
                byte[] bytes = Files.readAllBytes(skinPath);
                String textureAsset = translator.getSkinTextureAsset();
                FileCommonAsset asset = new FileCommonAsset(skinPath, textureAsset, bytes);
                CommonAssetModule module = CommonAssetModule.get();
                if (module != null) {
                    module.addCommonAsset("HyCraft", asset, false);
                    module.sendAssetsToPlayer(handler, Collections.singletonList(asset), false);
                }
            } catch (Exception e) {
                Logger.WARN.log("Failed to send skin asset for " + username + ": " + e.getMessage());
            }
        });

        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            if (event == null) return;
            com.hypixel.hytale.server.core.universe.PlayerRef playerRef =
                    event.getPlayer() != null ? event.getPlayer().getPlayerRef() : null;
            if (playerRef == null) return;

            applySkin(playerRef);
            scheduleReapply(playerRef);
        });
    }

    private void applySkin(com.hypixel.hytale.server.core.universe.PlayerRef playerRef) {
        String username = playerRef.getUsername();
        if (username == null || username.isBlank()) return;

        SkinTranslator translator = SkinTranslator.get();
        if (!translator.hasSkin(username)) return;

        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;
        Store<EntityStore> store = ref.getStore();
        if (store == null) return;

        String modelAssetId = translator.getModelAssetId(username);
        String textureAsset = translator.getSkinTextureAsset();
        SkinTranslator.applyModelWithTexture(ref, store, modelAssetId, textureAsset);
    }

    private void scheduleReapply(com.hypixel.hytale.server.core.universe.PlayerRef playerRef) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (playerRef == null || !playerRef.isValid()) return;
            UUID worldId = playerRef.getWorldUuid();
            if (worldId == null) return;
            World world = Universe.get().getWorld(worldId);
            if (world != null) {
                world.execute(() -> applySkin(playerRef));
            }
        }, java.util.concurrent.CompletableFuture.delayedExecutor(2000L, java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Override
    protected void start() {
        if (configManager.getMain().isBuildResourcePack()) {
            ResourcePackBuilder.build();
        }
        MappingRegistry.init();
        disableUnsupportedFeatures();
        minecraftServerBootstrap.init();
    }

    private void disableUnsupportedFeatures() {
        for (World world : Universe.get().getWorlds().values()) {
            world.registerFeature(ClientFeature.CrouchSlide, false);
            world.registerFeature(ClientFeature.SprintForce, false);
            world.registerFeature(ClientFeature.Mantling, false);
            world.registerFeature(ClientFeature.SafetyRoll, false);
            world.registerFeature(ClientFeature.SplitVelocity, false);
            world.registerFeature(ClientFeature.DisplayCombatText, false);
            world.registerFeature(ClientFeature.DisplayHealthBars, false);
        }
    }

    @Override
    protected void shutdown() {
        minecraftServerBootstrap.shutdown();
    }

    @Override
    public HyCraftConnection connectionByUUID(UUID uuid) {
        return minecraftServerBootstrap.getConnection(uuid);
    }

    @Override
    public Map<UUID, HyCraftConnection> onlineConnections() {
        return Collections.unmodifiableMap(minecraftServerBootstrap.getConnectionsByUUID());
    }

    @Override
    public void openGui(UUID uuid, HyCraftGui gui) {
        ClientConnection connection = minecraftServerBootstrap.getConnection(uuid);
        if (connection == null) return;
        GuiManager.get().openGui(connection, gui);
    }

    @Override
    public void closeGui(UUID uuid) {
        ClientConnection connection = minecraftServerBootstrap.getConnection(uuid);
        if (connection == null) return;
        GuiManager.get().closeGui(connection);
    }

    @Override
    public HyCraftGui getOpenedGui(UUID uuid) {
        ClientConnection connection = minecraftServerBootstrap.getConnection(uuid);
        if (connection == null) return null;
        return connection.getOpenedGui();
    }
}