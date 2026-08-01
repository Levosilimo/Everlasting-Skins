package levosilimo.everlastingskins.harness;

import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Captures packets sent through NetHandlerPlayServer.sendPacket. Attach with
 * attachTo(handler), then assert with ofType/all/size.
 */
public class PacketLog {

    private final List<Packet<?>> packets = new ArrayList<>();

    public void attachTo(NetHandlerPlayServer handler) {
        doAnswer(inv -> {
            packets.add(inv.getArgument(0));
            return null;
        }).when(handler).sendPacket(any(Packet.class));
    }

    public void record(Packet<?> p) {
        packets.add(p);
    }

    public <T extends Packet<?>> List<T> ofType(Class<T> type) {
        return packets.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .collect(Collectors.toList());
    }

    public List<Packet<?>> all() {
        return Collections.unmodifiableList(new ArrayList<>(packets));
    }

    public void clear() {
        packets.clear();
    }

    public int size() {
        return packets.size();
    }
}
