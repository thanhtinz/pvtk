package vn.pvtk.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import vn.pvtk.protocol.Packet;

/** Serializes outbound {@link Packet}s into wire frames. */
public final class PacketEncoder extends MessageToByteEncoder<Packet> {

    @Override
    protected void encode(ChannelHandlerContext ctx, Packet msg, ByteBuf out) {
        out.writeBytes(msg.toFrame());
    }
}
