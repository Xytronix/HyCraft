package es.edwardbelt.hycraft.protocol.packet.play;

import es.edwardbelt.hycraft.protocol.io.PacketBuffer;
import es.edwardbelt.hycraft.protocol.packet.Packet;

public class UseItemPacket implements Packet {
    private int hand;
    private int sequence;

    @Override
    public void read(PacketBuffer buffer) {
        this.hand = buffer.readVarInt();
        this.sequence = buffer.readVarInt();
    }

    public int getHand() {
        return hand;
    }

    public int getSequence() {
        return sequence;
    }
}
