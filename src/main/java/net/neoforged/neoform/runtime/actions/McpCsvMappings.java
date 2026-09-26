package net.neoforged.neoform.runtime.actions;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Legacy MCP mapping zips contain CSV files that map SRG member names to MCP member names.
 */
public record McpCsvMappings(Map<String, MemberMapping> methods, Map<String, MemberMapping> fields, Map<String, String> params) {
    public record MemberMapping(String name, String description) {
    }

    /**
     * Returns the legacy MCP placeholder name of the parameter with the given local variable table slot
     * of the given method, or {@code null} if the method has no CSV mapping. Legacy MCP parameter names
     * are keyed by the numeric method id (e.g. {@code p_1234_0_} is slot 0 of {@code func_1234_*}).
     */
    @Nullable
    public String parameterSrgName(String methodSrgName, int slot) {
        if (!methods.containsKey(methodSrgName)) {
            return null;
        }
        // The SRG name is func_<id>_<suffix>; the parameter id references <id>
        var matcher = SRG_METHOD_ID.matcher(methodSrgName);
        if (!matcher.matches()) {
            return null;
        }
        return "p_" + matcher.group(1) + "_" + slot + "_";
    }

    /**
     * Returns the MCP name of the parameter with the given local variable table slot of the given method,
     * or {@code null} if no mapping exists.
     */
    @Nullable
    public String parameterName(String methodSrgName, int slot) {
        var srgName = parameterSrgName(methodSrgName, slot);
        return srgName != null ? params.get(srgName) : null;
    }

    private static final Pattern SRG_METHOD_ID = Pattern.compile("func_(\\d+)_[a-zA-Z_]+");

    public static McpCsvMappings load(Path zipPath) throws IOException {
        try (var zip = new ZipFile(zipPath.toFile())) {
            return new McpCsvMappings(
                    readMemberMappings(zip, "methods.csv"),
                    readMemberMappings(zip, "fields.csv"),
                    readParamMappings(zip, "params.csv")
            );
        }
    }

    public String methodName(String srgName) {
        var mapping = methods.get(srgName);
        return mapping != null ? mapping.name() : srgName;
    }

    public String fieldName(String srgName) {
        var mapping = fields.get(srgName);
        return mapping != null ? mapping.name() : srgName;
    }

    private static Map<String, MemberMapping> readMemberMappings(ZipFile zip, String entryName) throws IOException {
        var entry = zip.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("MCP mappings zip " + zip.getName() + " does not contain " + entryName);
        }

        var result = new HashMap<String, MemberMapping>();
        try (var reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
            var header = reader.readLine();
            if (header == null) {
                return Map.of();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                var columns = parseCsvLine(line);
                if (columns.size() >= 2 && !columns.get(0).isBlank() && !columns.get(1).isBlank()) {
                    var description = columns.size() >= 4 ? columns.get(3) : "";
                    result.put(columns.get(0), new MemberMapping(columns.get(1), description));
                }
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, String> readParamMappings(ZipFile zip, String entryName) throws IOException {
        var entry = zip.getEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            return Map.of();
        }

        var result = new HashMap<String, String>();
        try (var reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
            var header = reader.readLine();
            if (header == null) {
                return Map.of();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                var columns = parseCsvLine(line);
                if (columns.size() >= 2 && !columns.get(0).isBlank() && !columns.get(1).isBlank()) {
                    result.put(columns.get(0), columns.get(1));
                }
            }
        }
        return Map.copyOf(result);
    }

    static List<String> parseCsvLine(String line) {
        var columns = new java.util.ArrayList<String>();
        var current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            var ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        columns.add(current.toString());
        return columns;
    }
}
