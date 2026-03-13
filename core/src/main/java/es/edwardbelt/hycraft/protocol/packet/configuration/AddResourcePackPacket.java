package es.edwardbelt.hycraft.protocol.packet.configuration;

import es.edwardbelt.hycraft.protocol.io.PacketBuffer;
import es.edwardbelt.hycraft.protocol.packet.Packet;
import lombok.AllArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
public class AddResourcePackPacket implements Packet {
    private UUID packId;
    private String url;
    private String hash;
    private boolean required;
    private String promptMessage;

    @Override
    public void write(PacketBuffer buffer) {
        buffer.writeUUID(packId);
        buffer.writeString(url);
        buffer.writeString(hash);
        buffer.writeBoolean(required);

        boolean hasPrompt = promptMessage != null && !promptMessage.isEmpty();
        buffer.writeBoolean(hasPrompt);
        if (hasPrompt) {
            buffer.writeString("{\"text\":\"" + promptMessage + "\"}");
        }
    }
}
