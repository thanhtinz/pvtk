package vn.pvtk.protocol.sprite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

/** Verifies the {@code .fr} frame-table parser against the recovered layout. */
class SpriteSheetTest {

    private static byte[] frame(int id, int x, int y, int w, int h) {
        return new byte[]{
                (byte) id,
                (byte) (x >>> 8), (byte) x,
                (byte) (y >>> 8), (byte) y,
                (byte) (w >>> 8), (byte) w,
                (byte) (h >>> 8), (byte) h
        };
    }

    @Test
    void parsesFrameTable() throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(3); // count
        b.write(frame(0, 0, 0, 32, 32));
        b.write(frame(1, 32, 0, 32, 32));
        b.write(frame(2, 64, 0, 32, 32));

        SpriteSheet s = SpriteSheet.parse(b.toByteArray());
        assertEquals(3, s.size());
        assertEquals(32, s.frame(1).x());
        assertEquals(32, s.frame(2).w());
        assertTrue(s.fitsWithin(256, 320));
        assertFalse(s.fitsWithin(50, 50)); // frame 2 (x=64) overflows
    }

    @Test
    void truncatedTableStopsCleanly() {
        // count says 5 but only one full frame of data follows.
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes(new byte[]{5});
        b.writeBytes(frame(0, 0, 0, 16, 16));
        SpriteSheet s = SpriteSheet.parse(b.toByteArray());
        assertEquals(1, s.size());
    }
}
