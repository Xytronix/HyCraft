package es.edwardbelt.hycraft.protocol.packet.play;

import es.edwardbelt.hycraft.protocol.io.PacketBuffer;
import es.edwardbelt.hycraft.protocol.packet.Packet;

public class ClientStatusPacket implements Packet {
    private int actionId;

    @Override
    public void read(PacketBuffer buffer) {
        this.actionId = buffer.readVarInt();
    }

    public int getActionId() {
        return actionId;
    }
}
