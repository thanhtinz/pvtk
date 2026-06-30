package vn.pvtk.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import java.util.List;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.ProtocolConstants;

/**
 * Decodes the length-prefixed wire frames into {@link Packet} instances.
 *
 * <p>Frame layout: {@code [length:u16][command:u16][payload:length-4]}. A length
 * of {@code 0xFFFF} is a keep-alive marker and is silently consumed. This is the
 * Netty equivalent of the original client's blocking reader thread.
 */
public final class PacketDecoder extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (true) {
            if (in.readableBytes() < 2) {
                return;
            }
            int idx = in.readerIndex();
            int length = in.getUnsignedShort(idx);

            if (length == Packet.KEEPALIVE_LENGTH) {
                in.skipBytes(2); // keep-alive ping, nothing to dispatch
                continue;
            }
            if (length < Packet.HEADER_SIZE || length > ProtocolConstants.MAX_FRAME_SIZE) {
                ctx.close();
                return;
            }
            if (in.readableBytes() < length) {
                return; // wait for the rest of the frame
            }
            in.skipBytes(2); // length
            int command = in.readUnsignedShort();
            byte[] payload = new byte[length - Packet.HEADER_SIZE];
            in.readBytes(payload);
            out.add(new Packet(command, payload));
        }
    }
}
