package es.edwardbelt.hycraft.network.handler.minecraft.play;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import es.edwardbelt.hycraft.network.handler.PacketHandler;
import es.edwardbelt.hycraft.network.handler.hytale.manager.interaction.InteractionContext;
import es.edwardbelt.hycraft.network.handler.hytale.manager.interaction.InteractionExtractorResponse;
import es.edwardbelt.hycraft.network.handler.hytale.manager.interaction.InteractionManager;
import es.edwardbelt.hycraft.network.player.ClientConnection;
import es.edwardbelt.hycraft.protocol.packet.play.UseItemPacket;

import java.util.HashMap;
import java.util.Map;

public class UseItemHandler implements PacketHandler<UseItemPacket> {
    @Override
    public void handle(UseItemPacket packet, ClientConnection connection) {
        if (packet.getHand() == 1) return;

        Ref<EntityStore> entityRef = connection.getPlayerRef().getReference();
        Store<EntityStore> store = entityRef.getStore();
        World world = store.getExternalData().getWorld();

        world.execute(() -> {
            LivingEntity playerEntity = store.getComponent(entityRef, Player.getComponentType());
            Inventory inventory = playerEntity.getInventory();
            com.hypixel.hytale.server.core.inventory.ItemStack handItem = inventory.getActiveHotbarItem();
            if (handItem == null) return;

            Item item = handItem.getItem();
            if (item == null) return;

            String interactionId = item.getInteractions().get(InteractionType.Secondary);
            if (interactionId == null) return;

            RootInteraction rootInteraction = RootInteraction.getAssetMap().getAsset(interactionId);
            if (rootInteraction == null) return;

            Interaction mainInteraction = InteractionManager.getInteractionFromRoot(rootInteraction);
            if (mainInteraction == null) return;

            Map<String, String> interactionVars = item.getInteractionVars() != null ? item.getInteractionVars() : new HashMap<>();
            InteractionExtractorResponse result = InteractionManager.get().extract(new InteractionContext(connection, interactionVars), mainInteraction);
            result.getInteractionPath().forEach(i -> InteractionManager.get().playInteraction(connection, i, InteractionType.Secondary));
        });
    }
}
