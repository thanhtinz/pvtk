package vn.pvtk.protocol.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for the original game's tab-separated content tables (the {@code *.txt}
 * files shipped in {@code assets/}: {@code item.txt}, {@code monster.txt},
 * {@code skill.txt}, {@code shop.txt}, ...).
 *
 * <p>These files are UTF-16LE (BOM {@code FF FE}), tab-delimited, with a header
 * row naming the columns. This loader auto-detects the byte-order-mark
 * (UTF-16LE / UTF-16BE / UTF-8) and exposes each data row as a column-keyed map,
 * so the server can load the real item/monster/skill database directly from the
 * preserved assets.
 */
public final class DataTable {

    private final List<String> columns;
    private final List<Map<String, String>> rows;

    private DataTable(List<String> columns, List<Map<String, String>> rows) {
        this.columns = columns;
        this.rows = rows;
    }

    public List<String> columns() {
        return columns;
    }

    public List<Map<String, String>> rows() {
        return rows;
    }

    public int size() {
        return rows.size();
    }

    public Map<String, String> row(int i) {
        return rows.get(i);
    }

    public static DataTable load(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return parse(bytes);
    }

    public static DataTable load(InputStream in) throws IOException {
        return parse(in.readAllBytes());
    }

    static DataTable parse(byte[] bytes) {
        Charset charset = StandardCharsets.UTF_8;
        int offset = 0;
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            charset = StandardCharsets.UTF_16LE;
            offset = 2;
        } else if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            charset = StandardCharsets.UTF_16BE;
            offset = 2;
        } else if (bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }

        String text = new String(bytes, offset, bytes.length - offset, charset);
        String[] lines = text.split("\r\n|\n|\r");

        List<String> columns = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();
        if (lines.length == 0) {
            return new DataTable(columns, rows);
        }

        for (String c : splitTabs(lines[0])) {
            columns.add(c.trim());
        }
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            String[] cells = splitTabs(lines[i]);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < columns.size(); c++) {
                row.put(columns.get(c), c < cells.length ? cells[c].trim() : "");
            }
            rows.add(row);
        }
        return new DataTable(columns, rows);
    }

    private static String[] splitTabs(String line) {
        return line.split("\t", -1);
    }
}
