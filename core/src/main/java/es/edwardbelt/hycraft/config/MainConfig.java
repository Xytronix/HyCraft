package es.edwardbelt.hycraft.config;

import es.edwardbelt.hycraft.config.annotation.ConfigProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class MainConfig implements Config {
    @ConfigProperty("port")
    private int port;

    @ConfigProperty("player_prefix")
    private String playerPrefix;

    @ConfigProperty("item_notification")
    private String itemNotification;

    @ConfigProperty("join_message")
    private List<String> joinMessage;

    @ConfigProperty("log_debug")
    private boolean logDebug;

    @ConfigProperty("build_resource_pack")
    private boolean buildResourcePack;

    @ConfigProperty("separate_player_uuids")
    private boolean separatePlayerUuids;

    @ConfigProperty("skin_mc_to_hytale")
    private boolean skinMcToHytale;

    @ConfigProperty("skin_hytale_to_mc")
    private boolean skinHytaleToMc;

    @ConfigProperty("mineskin_api_key")
    private String mineskinApiKey;
}
