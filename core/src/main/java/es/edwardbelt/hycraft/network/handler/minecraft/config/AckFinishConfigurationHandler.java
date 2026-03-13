package es.edwardbelt.hycraft.network.handler.minecraft.config;

import es.edwardbelt.hycraft.HyCraft;
import es.edwardbelt.hycraft.network.handler.PacketHandler;
import es.edwardbelt.hycraft.network.handler.hytale.HytaleUtil;
import es.edwardbelt.hycraft.network.player.ClientConnection;
import es.edwardbelt.hycraft.network.skin.SkinTranslator;
import es.edwardbelt.hycraft.protocol.ConnectionState;
import es.edwardbelt.hycraft.protocol.packet.configuration.AckFinishConfigurationPacket;

public class AckFinishConfigurationHandler implements PacketHandler<AckFinishConfigurationPacket> {
    @Override
    public void handle(AckFinishConfigurationPacket packet, ClientConnection connection) {
        connection.setState(ConnectionState.PLAY);

        if (HyCraft.get().getConfigManager().getMain().isSkinMcToHytale()) {
            SkinTranslator.get().downloadAndSaveSkin(connection);
        }

        HytaleUtil.createPlayer(connection);
    }
}
