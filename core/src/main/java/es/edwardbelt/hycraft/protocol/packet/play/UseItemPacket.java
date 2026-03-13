package es.edwardbelt.hycraft.protocol.packet.play;

import es.edwardbelt.hycraft.protocol.io.PacketBuffer;
import es.edwardbelt.hycraft.protocol.packet.Packet;
import lombok.Getter;

@Getter
public class UseItemPacket implements Packet {
    private int hand;
    private int sequence;
    private float yaw;
    private float pitch;

    @Override
    public void read(PacketBuffer buffer) {
        this.hand = buffer.readVarInt();
        this.sequence = buffer.readVarInt();
        this.yaw = buffer.readFloat();
        this.pitch = buffer.readFloat();
    }
}
