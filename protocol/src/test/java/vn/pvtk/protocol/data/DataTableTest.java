package vn.pvtk.protocol.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Verifies the UTF-16 TSV content-table parser used for the game data files. */
class DataTableTest {

    @Test
    void parsesUtf16leTabSeparatedWithBom() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(0xFF);
        bos.write(0xFE); // UTF-16LE BOM
        bos.write("id\tname\tprice\n1\tKiếm sắt\t100\n2\tGiáp da\t250\n"
                .getBytes(StandardCharsets.UTF_16LE));

        DataTable t = DataTable.parse(bos.toByteArray());
        assertEquals(3, t.columns().size());
        assertEquals("id", t.columns().get(0));
        assertEquals(2, t.size());
        assertEquals("Kiếm sắt", t.row(0).get("name"));
        assertEquals("250", t.row(1).get("price"));
    }

    @Test
    void parsesPlainUtf8() {
        DataTable t = DataTable.parse("a\tb\n1\t2\n".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, t.size());
        assertEquals("2", t.row(0).get("b"));
        assertTrue(t.columns().contains("a"));
    }
}
