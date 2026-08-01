package levosilimo.everlastingskins.harness;

import io.netty.buffer.Unpooled;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Round-trips a packet through PacketBuffer for byte-level assertions.
 * The 1.12.2 analog of EmbeddedChannel.write/read on newer versions.
 */
public final class WireSerializer {

    private WireSerializer() {
    }

    public static byte[] serialize(Packet<?> packet) {
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        try {
            packet.writePacketData(buf);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    public static int sizeOf(Packet<?> packet) {
        return serialize(packet).length;
    }
}
