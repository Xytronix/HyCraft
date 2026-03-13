package es.edwardbelt.hycraft.network.handler.hytale.world;

import com.hypixel.hytale.protocol.packets.world.UpdateWeather;
import es.edwardbelt.hycraft.network.handler.PacketHandler;
import es.edwardbelt.hycraft.network.player.ClientConnection;
import es.edwardbelt.hycraft.network.player.ProxyConnection;
import es.edwardbelt.hycraft.protocol.packet.play.GameEventPacket;

public class UpdateWeatherHandler implements PacketHandler<UpdateWeather> {
    private static final int GAME_EVENT_BEGIN_RAINING = 1;
    private static final int GAME_EVENT_END_RAINING = 2;
    private static final int GAME_EVENT_RAIN_LEVEL = 7;
    private static final int GAME_EVENT_THUNDER_LEVEL = 8;

    @Override
    public void handle(UpdateWeather packet, ProxyConnection connection) {
        ClientConnection conn = (ClientConnection) connection;
        if (packet.weatherIndex == 0) {
            conn.getChannel().writeAndFlush(new GameEventPacket(GAME_EVENT_END_RAINING, 0));
            conn.getChannel().writeAndFlush(new GameEventPacket(GAME_EVENT_RAIN_LEVEL, 0));
            conn.getChannel().writeAndFlush(new GameEventPacket(GAME_EVENT_THUNDER_LEVEL, 0));
        } else {
            conn.getChannel().writeAndFlush(new GameEventPacket(GAME_EVENT_BEGIN_RAINING, 0));
            conn.getChannel().writeAndFlush(new GameEventPacket(GAME_EVENT_RAIN_LEVEL, 1.0f));

            if (packet.weatherIndex >= 3) {
                conn.getChannel().writeAndFlush(new GameEventPacket(GAME_EVENT_THUNDER_LEVEL, 1.0f));
            }
        }
    }
}
