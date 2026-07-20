package vn.pvtk.protocol.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import org.junit.jupiter.api.Test;

/** Locks the reverse-engineered {@code .ui} widget-tree layout. */
class UiScreenTest {

    @Test
    void parsesWidgetTreeWithBoundsAndText() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(bytes);

        o.writeByte(1); // one preamble widget

        // preamble[0]: a container (g=50) with bounds, holding one bh label.
        o.writeByte(50);          // type g
        o.writeByte(0);           // 0 view-model slots
        o.writeByte(1);           // 1 attribute
        o.writeByte(1);           // attr 1 = bounds
        o.writeInt(0); o.writeInt(0); o.writeInt(200); o.writeInt(100);
        o.writeByte(1);           // 1 child
        // child: a bh (type 4) text widget.
        o.writeByte(4);           // type bh
        o.writeByte(0);           // 0 slots
        o.writeByte(2);           // 2 attributes
        o.writeByte(1);           // attr 1 bounds
        o.writeInt(10); o.writeInt(12); o.writeInt(80); o.writeInt(20);
        o.writeByte(12);          // attr 12 on bh = utf text + 3 ints
        o.writeUTF("Tuyên Chiến");
        o.writeInt(500); o.writeInt(0); o.writeInt(0);
        o.writeByte(0);           // child has no children

        // root widget: empty container.
        o.writeByte(50);
        o.writeByte(0);
        o.writeByte(0);
        o.writeByte(0);

        UiScreen s = UiScreen.parse(bytes.toByteArray());

        assertEquals(1, s.preamble().size());
        UiScreen.Widget container = s.preamble().get(0);
        assertEquals(50, container.type);
        assertEquals(200, container.w);
        assertEquals(100, container.h);
        assertEquals(1, container.children.size());

        UiScreen.Widget label = container.children.get(0);
        assertEquals("bh", label.typeName());
        assertEquals(10, label.x);
        assertEquals(20, label.h);
        assertEquals("Tuyên Chiến", label.text);
        assertEquals(50, s.root().type);
    }
}
