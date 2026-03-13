package es.edwardbelt.hycraft.network.handler.hytale.data;

import es.edwardbelt.hycraft.protocol.io.PacketBuffer;

import java.util.HashMap;
import java.util.Map;

public class HyShortPalette implements HyPalette {
    private final Map<Short, Integer> internalToExternal = new HashMap<>();
    private final short[] blocks = new short[32768];

    @Override
    public int getPaletteSize() {
        return internalToExternal.size();
    }

    @Override
    public void deserialize(PacketBuffer buffer) {
        short internalToExternalSize = buffer.readShortLE();
        for (short s = 0; s < internalToExternalSize; s++) {
            short internalId = buffer.readShortLE();
            int externalId = buffer.readIntLE();
            short count = buffer.readShortLE();

            internalToExternal.put(internalId, externalId);
        }

        for (int i = 0; i < 32768; i++) {
            blocks[i] = buffer.readShortLE();
        }
    }

    @Override
    public int getBlock(int index) {
        short paletteIndex = blocks[index];
        Integer externalId = internalToExternal.get(paletteIndex);
        return externalId != null ? externalId : 0;
    }
}
