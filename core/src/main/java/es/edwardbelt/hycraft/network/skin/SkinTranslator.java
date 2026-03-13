package es.edwardbelt.hycraft.network.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import es.edwardbelt.hycraft.HyCraft;
import es.edwardbelt.hycraft.network.handler.minecraft.data.profile.Property;
import es.edwardbelt.hycraft.network.player.ClientConnection;
import es.edwardbelt.hycraft.util.Logger;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SkinTranslator {
    private static final SkinTranslator INSTANCE = new SkinTranslator();
    public static SkinTranslator get() { return INSTANCE; }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final String SKINS_DIR = "mods/HyCraft/skins";
    private static final String CLASSIC_MODEL_ASSET_ID = "MCSkins_Model";
    private static final String SLIM_MODEL_ASSET_ID = "MCSkins_Model_Slim";
    private static final String SKIN_TEXTURE_ASSET = "Characters/minecraft_skin.png";

    private final Map<String, List<Property>> mcSkinCache = new ConcurrentHashMap<>();

    // --- MC -> Hytale (CommonAsset approach) ---

    public boolean downloadAndSaveSkin(ClientConnection connection) {
        try {
            String[] skinData = extractSkinData(connection);
            if (skinData == null) return false;

            String textureUrl = skinData[0];
            String modelType = skinData[1];

            BufferedImage skin = downloadImage(textureUrl);
            if (skin == null) return false;

            BufferedImage normalized = normalizeMinecraftSkin(skin);
            if (normalized == null) return false;

            Path skinPath = getSkinPath(connection.getUsername());
            Files.createDirectories(skinPath.getParent());
            ImageIO.write(normalized, "png", skinPath.toFile());

            Path modelPath = getModelTypePath(connection.getUsername());
            Properties props = new Properties();
            props.setProperty("model", modelType);
            try (var out = Files.newOutputStream(modelPath)) {
                props.store(out, null);
            }

            return true;
        } catch (Exception e) {
            Logger.WARN.log("Failed to download MC skin for " + connection.getUsername() + ": " + e.getMessage());
            return false;
        }
    }

    public Path getSkinPath(String username) {
        return Paths.get(SKINS_DIR, sanitizeUsername(username) + ".png");
    }

    public Path getModelTypePath(String username) {
        return Paths.get(SKINS_DIR, sanitizeUsername(username) + ".properties");
    }

    public boolean hasSkin(String username) {
        return Files.exists(getSkinPath(username));
    }

    public String getSkinTextureAsset() {
        return SKIN_TEXTURE_ASSET;
    }

    public String getModelAssetId(String username) {
        Path modelPath = getModelTypePath(username);
        if (Files.exists(modelPath)) {
            try (var in = Files.newInputStream(modelPath)) {
                Properties props = new Properties();
                props.load(in);
                String model = props.getProperty("model", "classic");
                if ("slim".equalsIgnoreCase(model.trim())) {
                    return SLIM_MODEL_ASSET_ID;
                }
            } catch (Exception ignored) {}
        }
        return CLASSIC_MODEL_ASSET_ID;
    }

    public static void applyModelWithTexture(Ref<EntityStore> ref, Store<EntityStore> store, String modelAssetId, String texturePath) {
        ModelAsset modelAsset = (ModelAsset) ModelAsset.getAssetMap().getAsset(modelAssetId);
        if (modelAsset == null) {
            Logger.WARN.log("Model asset not found: " + modelAssetId);
            return;
        }

        Map<String, String> randomAttachmentIds = modelAsset.generateRandomAttachmentIds();
        ModelAttachment[] attachments = modelAsset.getAttachments(randomAttachmentIds);
        Model model = new Model(
                modelAsset.getId(),
                1.0F,
                randomAttachmentIds,
                attachments,
                modelAsset.getBoundingBox(),
                modelAsset.getModel(),
                texturePath,
                modelAsset.getGradientSet(),
                modelAsset.getGradientId(),
                modelAsset.getEyeHeight(),
                modelAsset.getCrouchOffset(),
                modelAsset.getSittingOffset(),
                modelAsset.getSleepingOffset(),
                modelAsset.getAnimationSetMap(),
                modelAsset.getCamera(),
                modelAsset.getLight(),
                modelAsset.getParticles(),
                modelAsset.getTrails(),
                modelAsset.getPhysicsValues(),
                modelAsset.getDetailBoxes(),
                modelAsset.getPhobia(),
                modelAsset.getPhobiaModelAssetId()
        );
        store.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(model));
        PlayerSkinComponent skinComponent = store.getComponent(ref, PlayerSkinComponent.getComponentType());
        if (skinComponent != null) {
            skinComponent.setNetworkOutdated();
        }
    }

    public static BufferedImage normalizeMinecraftSkin(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width == 256 && height == 256) return image;

        BufferedImage base = null;
        if (width == 64 && height == 64) {
            base = image;
        } else if (width == 64 && height == 32) {
            BufferedImage expanded = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            expanded.getGraphics().drawImage(image, 0, 0, null);
            base = expanded;
        }
        if (base == null) return null;

        return scaleNearest(base, 256, 256);
    }

    private static BufferedImage scaleNearest(BufferedImage source, int width, int height) {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.drawImage(source, 0, 0, width, height, null);
        g2d.dispose();
        return scaled;
    }

    private String[] extractSkinData(ClientConnection connection) {
        try {
            Property texturesProp = connection.getProfile().getProperties().stream()
                    .filter(p -> "textures".equals(p.getName()))
                    .findFirst()
                    .orElse(null);
            if (texturesProp == null) return null;

            String decoded = new String(Base64.getDecoder().decode(texturesProp.getValue()));
            JsonObject json = JsonParser.parseString(decoded).getAsJsonObject();
            JsonObject textures = json.getAsJsonObject("textures");
            if (textures == null) return null;
            JsonObject skinObj = textures.getAsJsonObject("SKIN");
            if (skinObj == null) return null;
            String url = skinObj.get("url").getAsString();
            String model = skinObj.has("metadata") ?
                    skinObj.getAsJsonObject("metadata").get("model").getAsString() : "classic";
            return new String[]{url, model};
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitizeUsername(String username) {
        if (username == null || username.isBlank()) return "player";
        return username.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    // --- Hytale -> MC ---

    public List<Property> buildMcSkinProperties(UUID hytaleUuid) {
        String cacheKey = hytaleUuid.toString();
        List<Property> cached = mcSkinCache.get(cacheKey);
        if (cached != null) return cached;

        try {
            String apiKey = HyCraft.get().getConfigManager().getMain().getMineskinApiKey();
            List<Property> props;
            if (apiKey != null && !apiKey.isEmpty()) {
                props = buildSignedSkin(hytaleUuid, apiKey);
            } else {
                props = buildFallbackSkin(hytaleUuid);
            }

            if (!props.isEmpty()) {
                mcSkinCache.put(cacheKey, props);
            }
            return props;
        } catch (Exception e) {
            Logger.WARN.log("Failed to build MC skin from Hytale skin: " + e.getMessage());
            return List.of();
        }
    }

    private List<Property> buildSignedSkin(UUID hytaleUuid, String apiKey) {
        try {
            byte[] pngBytes = downloadMcSkinTexture(hytaleUuid);
            if (pngBytes == null) return buildFallbackSkin(hytaleUuid);

            String boundary = UUID.randomUUID().toString();
            byte[] body = buildMultipartBody(boundary, pngBytes);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mineskin.org/v2/generate"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return buildFallbackSkin(hytaleUuid);

            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonObject texture = json.getAsJsonObject("skin").getAsJsonObject("texture");
            String value = texture.get("value").getAsString();
            String signature = texture.get("signature").getAsString();

            return List.of(new Property("textures", value, signature));
        } catch (Exception e) {
            Logger.WARN.log("MineSkin API call failed, using fallback: " + e.getMessage());
            return buildFallbackSkin(hytaleUuid);
        }
    }

    private List<Property> buildFallbackSkin(UUID hytaleUuid) {
        try {
            byte[] pngBytes = downloadMcSkinTexture(hytaleUuid);
            if (pngBytes == null) return List.of();

            String textureUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);

            JsonObject texturesJson = new JsonObject();
            JsonObject skinObj = new JsonObject();
            skinObj.addProperty("url", textureUrl);
            JsonObject texturesWrapper = new JsonObject();
            texturesWrapper.add("SKIN", skinObj);
            texturesJson.add("textures", texturesWrapper);

            String value = Base64.getEncoder().encodeToString(texturesJson.toString().getBytes());
            return List.of(new Property("textures", value, null));
        } catch (Exception e) {
            Logger.WARN.log("Failed to build fallback MC skin: " + e.getMessage());
            return List.of();
        }
    }

    private byte[] downloadMcSkinTexture(UUID hytaleUuid) {
        return downloadBytes("https://crafthead.net/hytale/mc_skin/" + hytaleUuid.toString().replace("-", ""));
    }

    // --- Shared utilities ---

    private BufferedImage downloadImage(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) return null;
            return ImageIO.read(new ByteArrayInputStream(resp.body()));
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] downloadBytes(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) return null;
            return resp.body();
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] buildMultipartBody(String boundary, byte[] fileBytes) {
        String CRLF = "\r\n";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(("--" + boundary + CRLF).getBytes());
            baos.write(("Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"" + CRLF).getBytes());
            baos.write(("Content-Type: image/png" + CRLF).getBytes());
            baos.write(CRLF.getBytes());
            baos.write(fileBytes);
            baos.write((CRLF + "--" + boundary + "--" + CRLF).getBytes());
        } catch (Exception ignored) {}
        return baos.toByteArray();
    }
}
