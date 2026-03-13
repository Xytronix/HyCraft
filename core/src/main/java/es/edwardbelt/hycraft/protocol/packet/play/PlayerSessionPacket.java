package es.edwardbelt.hycraft.protocol.packet.play;

import es.edwardbelt.hycraft.protocol.io.PacketBuffer;
import es.edwardbelt.hycraft.protocol.packet.Packet;

public class PlayerSessionPacket implements Packet {
    private byte[] sessionId;
    private long expiresAt;
    private byte[] publicKey;
    private byte[] keySignature;

    @Override
    public void read(PacketBuffer buffer) {
        int sessionIdLength = buffer.readVarInt();
        this.sessionId = new byte[sessionIdLength];
        buffer.readBytes(sessionId);

        this.expiresAt = buffer.readLong();

        int publicKeyLength = buffer.readVarInt();
        this.publicKey = new byte[publicKeyLength];
        buffer.readBytes(publicKey);

        int keySignatureLength = buffer.readVarInt();
        this.keySignature = new byte[keySignatureLength];
        buffer.readBytes(keySignature);
    }
}
