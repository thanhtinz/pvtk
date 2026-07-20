package vn.pvtk.protocol.sprite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

/** Locks the reverse-engineered {@code .spr} / sprite-{@code .fr} binary layout. */
class SprSpriteTest {

    @Test
    void parsesThreeSections() {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        // Fragments: 2 -> (type, nameId:s16 big-endian)
        b.write(2);
        b.write(5); b.write(0); b.write(100);       // type 5, nameId 100
        b.write(3); b.write(0xFF); b.write(0xFF);   // type 3, nameId -1
        // Frames: 1 frame with 2 layers (frag, sub, dx:s8, dy:s8, flags)
        b.write(1);
        b.write(2);
        b.write(5); b.write(0); b.write(0xFE); b.write(3); b.write(1);   // dx=-2, flip
        b.write(3); b.write(1); b.write(4); b.write(0xFB); b.write(0);   // dy=-5
        // Animations: 1 anim, kind 16 (has dx/dy), 2 keys
        b.write(1);
        b.write(2); b.write(16); b.write(7); b.write(0);
        b.write(0); b.write(2); b.write(1); b.write(1);
        b.write(0); b.write(3); b.write(0xFF); b.write(0xFF);

        SprSprite s = SprSprite.parse(b.toByteArray());

        assertEquals(2, s.fragments().size());
        assertEquals(5, s.fragments().get(0).typeId());
        assertEquals(100, s.fragments().get(0).nameId());
        assertEquals(-1, s.fragments().get(1).nameId());

        assertEquals(1, s.frames().size());
        SprSprite.Layer l0 = s.frames().get(0).layers().get(0);
        assertEquals(-2, l0.dx());
        assertEquals(3, l0.dy());
        assertTrue(l0.flipX());
        SprSprite.Layer l1 = s.frames().get(0).layers().get(1);
        assertEquals(-5, l1.dy());
        assertFalse(l1.flipX());

        assertEquals(1, s.animations().size());
        SprSprite.Animation a = s.animations().get(0);
        assertEquals(7, a.id());
        assertEquals(16, a.kind());
        assertEquals(2, a.keys().size());
        assertEquals(2, a.keys().get(0).duration());
        assertEquals(1, a.keys().get(0).dx());
        assertEquals(-1, a.keys().get(1).dy());
    }

    @Test
    void parsesPieceTable() {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.write(2);                                 // 2 pieces
        b.write(0); b.write(1); b.write(2); b.write(3); b.write(4);
        b.write(7); b.write(8); b.write(9); b.write(10); b.write(11);

        SprSprite.Fr fr = SprSprite.Fr.parse(b.toByteArray());
        assertEquals(2, fr.pieces().size());
        assertEquals(1, fr.byId(0).x());
        assertEquals(11, fr.byId(7).h());
        assertTrue(fr.fitsWithin(64, 64));
        assertFalse(fr.fitsWithin(5, 5)); // piece 7 (x=8) overflows
    }
}
