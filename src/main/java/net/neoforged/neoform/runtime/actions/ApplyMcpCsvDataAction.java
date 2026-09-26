package net.neoforged.neoform.runtime.actions;

import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.srgutils.IMappingFile;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Applies legacy MCP CSV metadata that is not represented in the bytecode:
 * member comments from the {@code desc} column in {@code methods.csv} and {@code fields.csv}.
 * Parameter names from {@code params.csv} are also applied here as a fallback for any
 * parameter that was not renamed on the bytecode level (e.g. names missing from the
 * TSRG2 parameter mappings used by the class remapper).
 */
public class ApplyMcpCsvDataAction extends BuiltInAction {
    private static final Pattern PARAMETER_FINDER = Pattern.compile("p_[a-z]?\\d+_\\d+_");
    private static final Pattern FIELD_DECLARATION = Pattern.compile("^\\s*(?:(?:public|protected|private)\\s+)?(?:(?:static|final|transient|volatile)\\s+)*[\\w$\\[\\].<>?,]+(?:\\s+[\\w$\\[\\].<>?,]+)*\\s+(\\w+)\\s*(?:=|;|,).*$");
    private static final Pattern METHOD_DECLARATION = Pattern.compile("^\\s*(?:(?:public|protected|private)\\s+)?(?:(?:static|final|abstract|native|synchronized|strictfp|default)\\s+)*(?:<[^>]+>\\s+)?[\\w$\\[\\].<>?,]+(?:\\s+[\\w$\\[\\].<>?,]+)*\\s+(\\w+)\\s*\\(.*$");

    @Override
    public void run(ProcessingEnvironment environment) throws IOException {
        var csvMappings = McpCsvMappings.load(environment.getRequiredInputPath("csvMappings"));
        var srgToMcp = IMappingFile.load(environment.getRequiredInputPath("mappings").toFile());
        var docsByClass = buildDocsByClass(srgToMcp, csvMappings);

        var sourcesPath = environment.getRequiredInputPath("sources");
        var outputPath = environment.getOutputPath("output");

        try (var zipIn = new ZipInputStream(new BufferedInputStream(java.nio.file.Files.newInputStream(sourcesPath)));
             var zipOut = new ZipOutputStream(new BufferedOutputStream(java.nio.file.Files.newOutputStream(outputPath)))) {
            for (var entry = zipIn.getNextEntry(); entry != null; entry = zipIn.getNextEntry()) {
                zipOut.putNextEntry(entry);

                if (!entry.isDirectory() && entry.getName().endsWith(".java")) {
                    var sourceCode = new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                    var className = entry.getName().substring(0, entry.getName().length() - ".java".length());
                    var mappedSource = applyToSource(sourceCode, docsByClass.forSourceFile(className), csvMappings.params());
                    zipOut.write(mappedSource.getBytes(StandardCharsets.UTF_8));
                } else {
                    zipIn.transferTo(zipOut);
                }
                zipOut.closeEntry();
            }
        }
    }

    private static DocsByClass buildDocsByClass(IMappingFile srgToMcp, McpCsvMappings csvMappings) {
        var result = new DocsByClass();
        for (var mappedClass : srgToMcp.getClasses()) {
            var classDocs = result.getOrCreate(mappedClass.getMapped());

            for (var mappedField : mappedClass.getFields()) {
                var csvMapping = csvMappings.fields().get(mappedField.getOriginal());
                if (csvMapping != null && !csvMapping.description().isBlank()) {
                    // The sources still use SRG names at this point; the docs must be keyed by the
                    // SRG (original) field name to be found in the sources.
                    classDocs.fieldDocs().put(mappedField.getOriginal(), csvMapping.description());
                }
            }

            for (var mappedMethod : mappedClass.getMethods()) {
                var csvMapping = csvMappings.methods().get(mappedMethod.getOriginal());
                if (csvMapping != null && !csvMapping.description().isBlank()) {
                    classDocs.methodDocs().put(mappedMethod.getOriginal(), csvMapping.description());
                }
            }
        }
        return result;
    }

    static String applyToSource(String sourceCode, ClassDocs classDocs) {
        return applyToSource(sourceCode, classDocs, Map.of());
    }

    static String applyToSource(String sourceCode, ClassDocs classDocs, Map<String, String> params) {
        var withParams = replaceParams(sourceCode, params);
        return insertMemberDocs(withParams, classDocs);
    }

    private static String replaceParams(String sourceCode, Map<String, String> params) {
        if (params.isEmpty()) {
            return sourceCode;
        }

        var matcher = PARAMETER_FINDER.matcher(sourceCode);
        return matcher.replaceAll(matchResult -> {
            var paramName = params.get(matchResult.group());
            return paramName != null ? Matcher.quoteReplacement(paramName) : matchResult.group();
        });
    }

    private static String insertMemberDocs(String sourceCode, ClassDocs classDocs) {
        if (classDocs.isEmpty()) {
            return sourceCode;
        }

        var lines = sourceCode.split("\\R", -1);
        var result = new ArrayList<String>(lines.length);
        var annotations = new ArrayList<String>();

        for (var line : lines) {
            if (line.trim().startsWith("@")) {
                annotations.add(line);
                continue;
            }

            var doc = findDoc(line, classDocs);
            if (doc != null && !hasExistingDoc(result)) {
                addJavadoc(result, indentationOf(line), doc);
            }

            result.addAll(annotations);
            annotations.clear();
            result.add(line);
        }

        result.addAll(annotations);
        return String.join("\n", result);
    }

    private static String findDoc(String line, ClassDocs classDocs) {
        var methodMatcher = METHOD_DECLARATION.matcher(line);
        if (methodMatcher.matches()) {
            var doc = classDocs.methodDocs().get(methodMatcher.group(1));
            if (doc != null && !doc.isBlank()) {
                return doc;
            }
        }

        var fieldMatcher = FIELD_DECLARATION.matcher(line);
        if (fieldMatcher.matches()) {
            var doc = classDocs.fieldDocs().get(fieldMatcher.group(1));
            if (doc != null && !doc.isBlank()) {
                return doc;
            }
        }

        return null;
    }

    private static boolean hasExistingDoc(List<String> emittedLines) {
        for (int i = emittedLines.size() - 1; i >= 0; i--) {
            var line = emittedLines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            return line.equals("*/") || line.startsWith("/**") || line.startsWith("//");
        }
        return false;
    }

    private static String indentationOf(String line) {
        var i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }

    private static void addJavadoc(List<String> result, String indent, String rawDoc) {
        // MCP CSVs encode line breaks in multi-line comments as a literal backslash followed by 'n'
        var doc = rawDoc.replace("*/", "* /").replace("\\n", "\n").strip();
        if (doc.length() <= 100 && !doc.contains("\n")) {
            result.add(indent + "/** " + doc + " */");
            return;
        }

        result.add(indent + "/**");
        for (var line : wrap(doc, 100)) {
            result.add(indent + " * " + line);
        }
        result.add(indent + " */");
    }

    private static List<String> wrap(String value, int maxLength) {
        var result = new ArrayList<String>();
        for (var paragraph : value.split("\\R")) {
            var remaining = paragraph.strip();
            while (remaining.length() > maxLength) {
                var breakAt = remaining.lastIndexOf(' ', maxLength);
                if (breakAt <= 0) {
                    breakAt = maxLength;
                }
                result.add(remaining.substring(0, breakAt).stripTrailing());
                remaining = remaining.substring(breakAt).stripLeading();
            }
            if (!remaining.isEmpty()) {
                result.add(remaining);
            }
        }
        return result;
    }

    record ClassDocs(Map<String, String> fieldDocs, Map<String, String> methodDocs) {
        boolean isEmpty() {
            return fieldDocs.isEmpty() && methodDocs.isEmpty();
        }
    }

    private static final class DocsByClass {
        private final Map<String, ClassDocs> docs = new HashMap<>();

        ClassDocs getOrCreate(String className) {
            return docs.computeIfAbsent(className, ignored -> new ClassDocs(new HashMap<>(), new HashMap<>()));
        }

        ClassDocs forSourceFile(String sourceClassName) {
            var result = new ClassDocs(new HashMap<>(), new HashMap<>());
            for (var entry : docs.entrySet()) {
                var className = entry.getKey();
                if (className.equals(sourceClassName) || className.startsWith(sourceClassName + "$")) {
                    result.fieldDocs().putAll(entry.getValue().fieldDocs());
                    result.methodDocs().putAll(entry.getValue().methodDocs());
                }
            }
            return result;
        }
    }
}
