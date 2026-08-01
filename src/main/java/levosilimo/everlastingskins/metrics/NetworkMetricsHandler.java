package levosilimo.everlastingskins.metrics;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelDuplexHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.HandlerNames;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-connection byte counter attached to the netty pipeline. Counts raw
 * ByteBuf traffic flowing through the connection (encoded payloads, not
 * logical packet objects), so it is a lower bound on real wire bytes.
 *
 * Attachment is best-effort: if the pipeline is not yet configured the
 * handler is skipped rather than risking a crash, and {@link #getOrAttach}
 * retries lazily once the pipeline is live.
 */
public class NetworkMetricsHandler extends ChannelDuplexHandler {

    public static final String HANDLER_NAME = "everlastingskins:net_metrics";

    private final AtomicLong outboundBytes = new AtomicLong();
    private final AtomicLong inboundBytes = new AtomicLong();

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        if (msg instanceof ByteBuf buf) {
            outboundBytes.addAndGet(buf.readableBytes());
        }
        ctx.write(msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf buf) {
            inboundBytes.addAndGet(buf.readableBytes());
        }
        ctx.fireChannelRead(msg);
    }

    public long outboundBytes() {
        return outboundBytes.get();
    }

    public long inboundBytes() {
        return inboundBytes.get();
    }

    /**
     * Returns the handler attached to the connection's pipeline, attaching a
     * new one in front of the encoder when absent. Returns null when the
     * channel or pipeline is not ready (e.g. in-memory test channels).
     */
    public static NetworkMetricsHandler getOrAttach(Connection connection) {
        try {
            Channel channel = connection.channel();
            if (channel == null) return null;
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline == null) return null;
            if (pipeline.get(HANDLER_NAME) instanceof NetworkMetricsHandler existing) {
                return existing;
            }
            NetworkMetricsHandler handler = new NetworkMetricsHandler();
            if (pipeline.get(HandlerNames.ENCODER) != null) {
                pipeline.addBefore(HandlerNames.ENCODER, HANDLER_NAME, handler);
            } else {
                pipeline.addLast(HANDLER_NAME, handler);
            }
            return handler;
        } catch (Exception e) {
            return null;
        }
    }
}
