package es.edwardbelt.hycraft.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.AssetModule;
import es.edwardbelt.hycraft.config.JsonConfig;
import es.edwardbelt.hycraft.util.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResourcePackBuilder {

    static final String PACK_PATH = "mods/HyCraft/resourcepack.zip";
    private static final String TEXTURES_DIR = "Common/BlockTextures";
    private static final String MC_TEXTURES_DIR = "assets/minecraft/textures/block/";

    private static final String PACK_MCMETA = """
            {
              "pack": {
                "pack_format": 34,
                "supported_formats": [34, 46],
                "description": "HyCraft - Hytale textures for Minecraft"
              }
            }
            """;

    public static boolean build() {
        File packFile = new File(PACK_PATH);
        if (packFile.exists()) {
            Logger.INFO.log("Resource pack already exists at " + PACK_PATH + ", skipping build");
            return true;
        }

        Path texturesDir = resolveTexturesDir();
        if (texturesDir == null) {
            return false;
        }

        Set<String> availableTextures = indexTextures(texturesDir);
        if (availableTextures.isEmpty()) {
            Logger.WARN.log("No block textures found in asset pack");
            return false;
        }

        Map<String, String> overrides = loadTextureOverrides();
        Map<String, String> reverseMap = buildReverseMap();

        packFile.getParentFile().mkdirs();
        int textureCount = 0;

        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(packFile))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write(PACK_MCMETA.getBytes());
            out.closeEntry();

            Set<String> written = new HashSet<>();

            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                String mcTexture = entry.getKey();
                String hytaleTexture = entry.getValue();

                String fileName = hytaleTexture + ".png";
                if (!availableTextures.contains(fileName)) continue;

                String destPath = MC_TEXTURES_DIR + mcTexture + ".png";
                if (written.add(destPath)) {
                    writeTexture(texturesDir.resolve(fileName), out, destPath);
                    textureCount++;
                }
            }

            for (Map.Entry<String, String> entry : reverseMap.entrySet()) {
                String mcBlock = entry.getKey();
                String hytaleKey = entry.getValue();

                String mainDest = MC_TEXTURES_DIR + mcBlock + ".png";
                if (!written.contains(mainDest)) {
                    String mainFile = resolveTexture(hytaleKey, "", availableTextures);
                    if (mainFile != null) {
                        writeTexture(texturesDir.resolve(mainFile), out, mainDest);
                        written.add(mainDest);
                        textureCount++;
                    }
                }

                String topDest = MC_TEXTURES_DIR + mcBlock + "_top.png";
                if (!written.contains(topDest)) {
                    String topFile = resolveTexture(hytaleKey, "_Top", availableTextures);
                    if (topFile != null) {
                        writeTexture(texturesDir.resolve(topFile), out, topDest);
                        written.add(topDest);
                        textureCount++;
                    }
                }
            }

        } catch (IOException e) {
            Logger.ERROR.log("Failed to build resource pack: " + e.getMessage());
            packFile.delete();
            return false;
        }

        Logger.INFO.log("Built resource pack with " + textureCount + " textures");
        return true;
    }

    private static Path resolveTexturesDir() {
        try {
            AssetPack basePack = AssetModule.get().getBaseAssetPack();
            Path dir = basePack.getRoot().resolve(TEXTURES_DIR);
            if (Files.isDirectory(dir)) {
                return dir;
            }
        } catch (Exception e) {
            Logger.WARN.log("AssetModule not available, cannot build resource pack: " + e.getMessage());
        }
        return null;
    }

    private static Set<String> indexTextures(Path texturesDir) {
        Set<String> textures = new HashSet<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(texturesDir, "*.png")) {
            for (Path p : stream) {
                textures.add(p.getFileName().toString());
            }
        } catch (IOException e) {
            Logger.ERROR.log("Failed to index block textures: " + e.getMessage());
        }
        return textures;
    }

    /**
     * Loads explicit MC texture -> Hytale texture overrides from texture_mappings.json.
     * Keys and values are filenames without .png extension.
     */
    private static Map<String, String> loadTextureOverrides() {
        Map<String, String> overrides = new LinkedHashMap<>();
        JsonConfig config = new JsonConfig("texture_mappings");
        JsonObject json = config.get();
        if (json == null) return overrides;

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (!entry.getValue().isJsonPrimitive()) continue;
            overrides.put(entry.getKey(), entry.getValue().getAsString());
        }
        return overrides;
    }

    /**
     * Tries multiple naming conventions to find a texture file.
     * Handles the mismatch between blocks.json keys (e.g. Wood_Oak_Trunk)
     * and asset names (e.g. Wood_Trunk_Oak_Side).
     */
    private static String resolveTexture(String hytaleKey, String suffix, Set<String> available) {
        String direct = hytaleKey + suffix + ".png";
        if (available.contains(direct)) return direct;

        if (suffix.isEmpty()) {
            String side = hytaleKey + "_Side.png";
            if (available.contains(side)) return side;
        }

        String swapped = tryTrunkSwap(hytaleKey, suffix);
        if (swapped != null && available.contains(swapped)) return swapped;

        return null;
    }

    /**
     * blocks.json uses "Wood_{TreeType}_Trunk" but assets use "Wood_Trunk_{TreeType}_Side".
     */
    private static String tryTrunkSwap(String hytaleKey, String suffix) {
        if (!hytaleKey.startsWith("Wood_") || !hytaleKey.contains("_Trunk")) return null;

        int trunkIdx = hytaleKey.indexOf("_Trunk");
        String treeType = hytaleKey.substring("Wood_".length(), trunkIdx);
        String trunkSuffix = hytaleKey.substring(trunkIdx + "_Trunk".length());

        if (trunkSuffix.isEmpty()) {
            String sideSuffix = suffix.isEmpty() ? "_Side" : suffix;
            return "Wood_Trunk_" + treeType + sideSuffix + ".png";
        }

        return "Wood_Trunk_" + treeType + trunkSuffix + suffix + ".png";
    }

    private static Map<String, String> buildReverseMap() {
        Map<String, String> reverseMap = new LinkedHashMap<>();

        JsonConfig config = new JsonConfig("mappings/blocks");
        JsonObject json = config.get();
        if (json == null) return reverseMap;

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            String hytaleKey = entry.getKey();
            if (hytaleKey.startsWith("_")) continue;

            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive()) continue;

            String mcBlock = value.getAsString();
            if (mcBlock.equals("air")) continue;

            int bracketIdx = mcBlock.indexOf('[');
            if (bracketIdx > 0) mcBlock = mcBlock.substring(0, bracketIdx);

            reverseMap.putIfAbsent(mcBlock, hytaleKey);
        }

        return reverseMap;
    }

    private static void writeTexture(Path source, ZipOutputStream dest, String destPath) throws IOException {
        dest.putNextEntry(new ZipEntry(destPath));
        Files.copy(source, dest);
        dest.closeEntry();
    }
}
