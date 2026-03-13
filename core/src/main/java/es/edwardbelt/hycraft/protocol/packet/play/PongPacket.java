package es.edwardbelt.hycraft.protocol.packet.play;

import es.edwardbelt.hycraft.protocol.io.PacketBuffer;
import es.edwardbelt.hycraft.protocol.packet.Packet;

public class PongPacket implements Packet {
    private int id;

    @Override
    public void read(PacketBuffer buffer) {
        this.id = buffer.readInt();
    }

    public int getId() {
        return id;
    }
}
