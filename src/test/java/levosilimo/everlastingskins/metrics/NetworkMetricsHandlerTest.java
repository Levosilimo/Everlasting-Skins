package levosilimo.everlastingskins.metrics;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NetworkMetricsHandlerTest {

    @Test
    @DisplayName("outbound and inbound ByteBufs are counted by readable bytes")
    void countsByteBufTraffic() {
        EmbeddedChannel channel = new EmbeddedChannel(new NetworkMetricsHandler());

        channel.writeOutbound(Unpooled.wrappedBuffer(new byte[100]));
        channel.writeInbound(Unpooled.wrappedBuffer(new byte[50]));

        NetworkMetricsHandler handler = channel.pipeline().get(NetworkMetricsHandler.class);
        assertNotNull(handler);
        assertEquals(100, handler.outboundBytes());
        assertEquals(50, handler.inboundBytes());
    }

    @Test
    @DisplayName("non-ByteBuf messages pass through uncounted")
    void ignoresNonByteBufMessages() {
        EmbeddedChannel channel = new EmbeddedChannel(new NetworkMetricsHandler());

        channel.writeOutbound("out-payload");
        channel.writeInbound("in-payload");

        NetworkMetricsHandler handler = channel.pipeline().get(NetworkMetricsHandler.class);
        assertEquals(0, handler.outboundBytes());
        assertEquals(0, handler.inboundBytes());
        assertEquals("out-payload", channel.readOutbound());
        assertEquals("in-payload", channel.readInbound());
    }

    @Test
    @DisplayName("data still flows through the pipeline after counting")
    void dataStillFlowsThrough() {
        EmbeddedChannel channel = new EmbeddedChannel(new NetworkMetricsHandler());

        channel.writeOutbound(Unpooled.wrappedBuffer(new byte[]{1, 2, 3}));
        channel.writeInbound(Unpooled.wrappedBuffer(new byte[]{4, 5}));

        ByteBuf out = (ByteBuf) channel.readOutbound();
        ByteBuf in = (ByteBuf) channel.readInbound();
        assertEquals(3, out.readableBytes());
        assertEquals(2, in.readableBytes());
    }
}
