package vn.pvtk.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import org.junit.jupiter.api.Test;

/** Verifies the wire codec is byte-for-byte faithful to the original protocol. */
class PacketTest {

    @Test
    void primitivesRoundTrip() {
        Packet w = new Packet(Opcodes.LOGIN);
        w.putBool(true).putByte(0x7F).putShort(0x1234).putInt(0x01020304)
                .putLong(0x1122334455667788L).putString("Phong Vân").putBytes(new byte[]{9, 8, 7});

        Packet r = new Packet(w.command(), w.payload());
        assertTrue(r.getBool());
        assertEquals(0x7F, r.getByte());
        assertEquals(0x1234, r.getShort() & 0xFFFF);
        assertEquals(0x01020304, r.getInt());
        assertEquals(0x1122334455667788L, r.getLong());
        assertEquals("Phong Vân", r.getString());
        assertArrayEquals(new byte[]{9, 8, 7}, r.getBytes());
        assertFalse(r.hasRemaining());
    }

    @Test
    void frameHeaderMatchesOriginalLayout() {
        Packet p = new Packet(10003);
        p.putShort(42);
        byte[] frame = p.toFrame();

        // [len:u16][cmd:u16][payload]
        int len = ((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF);
        int cmd = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        assertEquals(frame.length, len);
        assertEquals(6, len); // 4 header + 2 payload
        assertEquals(10003, cmd);
        assertEquals(42, ((frame[4] & 0xFF) << 8) | (frame[5] & 0xFF));
    }

    @Test
    void stringUsesUtf16beWithCharCountPrefix() {
        Packet p = new Packet(1);
        p.putString("AB"); // 2 chars
        byte[] payload = p.payload();
        // 3-byte length prefix == char count (2), then 'A'(00 41) 'B'(00 42)
        assertEquals(0, payload[0]);
        assertEquals(0, payload[1]);
        assertEquals(2, payload[2]);
        assertEquals(0x00, payload[3] & 0xFF);
        assertEquals('A', payload[4]);
        assertEquals(0x00, payload[5] & 0xFF);
        assertEquals('B', payload[6]);
        assertEquals(7, payload.length);
    }

    @Test
    void frameStreamRoundTrip() throws Exception {
        Packet p = new Packet(Opcodes.CHAT);
        p.putByte(1).putString("hi there");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Frame.write(bos, p);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()));
        Packet got = Frame.read(in);
        assertEquals(Opcodes.CHAT, got.command() & 0xFFFF);
        assertEquals(1, got.getByte());
        assertEquals("hi there", got.getString());
    }

    @Test
    void keepAliveDecodesToNull() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Frame.writeKeepAlive(bos);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bos.toByteArray()));
        assertNull(Frame.read(in));
    }

    @Test
    void oversizeFrameRejected() {
        Packet p = new Packet(1);
        byte[] big = new byte[Short.MAX_VALUE];
        p.putBytes(big); // pushes total over the u16 limit
        assertThrows(ProtocolException.class, p::toFrame);
    }

    @Test
    void opcodeRegistryRecovered() {
        assertEquals(245, Opcodes.all().size());
        assertEquals("CHAT_MSG", Opcodes.name(13509));
        assertTrue(Opcodes.isKnown(Opcodes.LOGIN));
        assertEquals("UNKNOWN_99999", Opcodes.name(99999));
    }
}
