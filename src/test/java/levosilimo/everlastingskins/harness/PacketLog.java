/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2025 Levosilimo
 * https://github.com/Levosilimo/Everlasting-Skins
 */

package levosilimo.everlastingskins.harness;

import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Captures packets sent through NetHandlerPlayServer.sendPacket. Attach with
 * attachTo(handler), then assert with ofType/all/size.
 */
public class PacketLog {

    private final List<Packet<?>> packets = new CopyOnWriteArrayList<>();

    public void attachTo(NetHandlerPlayServer handler) {
        doAnswer(inv -> {
            record(inv.getArgument(0));
            return null;
        }).when(handler).sendPacket(any(Packet.class));
    }

    public synchronized void record(Packet<?> p) {
        packets.add(p);
    }

    public synchronized <T extends Packet<?>> List<T> ofType(Class<T> type) {
        return packets.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .collect(Collectors.toList());
    }

    public synchronized List<Packet<?>> all() {
        return Collections.unmodifiableList(new ArrayList<>(packets));
    }

    public synchronized void clear() {
        packets.clear();
    }

    public synchronized int size() {
        return packets.size();
    }
}
