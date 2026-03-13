package es.edwardbelt.hycraft.protocol.packet.play;

import es.edwardbelt.hycraft.protocol.io.PacketBuffer;
import es.edwardbelt.hycraft.protocol.packet.Packet;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class LevelParticlesPacket implements Packet {
    private boolean overrideLimiter;
    private boolean alwaysShow;
    private double x;
    private double y;
    private double z;
    private float offsetX;
    private float offsetY;
    private float offsetZ;
    private float maxSpeed;
    private int count;
    private int particleId;

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeBoolean(overrideLimiter);
        buffer.writeBoolean(alwaysShow);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeFloat(offsetX);
        buffer.writeFloat(offsetY);
        buffer.writeFloat(offsetZ);
        buffer.writeFloat(maxSpeed);
        buffer.writeInt(count);
        buffer.writeVarInt(particleId);
    }
}
