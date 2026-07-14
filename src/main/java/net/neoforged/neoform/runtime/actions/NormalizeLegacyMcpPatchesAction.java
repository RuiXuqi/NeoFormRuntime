package net.neoforged.neoform.runtime.actions;

import net.neoforged.neoform.runtime.cache.CacheKeyBuilder;
import net.neoforged.neoform.runtime.engine.DataSource;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.neoform.runtime.utils.ZipUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Normalizes bare blank lines in legacy MCP unified diffs. Older patch writers emitted these lines without the
 * required context prefix, while current DiffPatch versions ignore them and then fail to match the declared hunk.
 */
public final class NormalizeLegacyMcpPatchesAction extends BuiltInAction {
    private final DataSource patches;

    public NormalizeLegacyMcpPatchesAction(DataSource patches) {
        this.patches = Objects.requireNonNull(patches, "patches");
    }

    @Override
    public void run(ProcessingEnvironment environment) throws IOException {
        var output = environment.getOutputPath("output");
        var entries = patches.archive().stream()
                .filter(entry -> entry.getName().startsWith(patches.folder()))
                .sorted(Comparator.comparing(ZipEntry::getName))
                .toList();

        try (var zipOut = new ZipOutputStream(Files.newOutputStream(output))) {
            for (var entry : entries) {
                zipOut.putNextEntry(ZipUtil.getStableEntry(entry.getName()));
                if (!entry.isDirectory()) {
                    try (var input = patches.archive().getInputStream(entry)) {
                        if (entry.getName().endsWith(".patch")) {
                            var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
                            var normalized = normalizePatchLines(reader.lines().toList());
                            zipOut.write((String.join("\n", normalized) + "\n").getBytes(StandardCharsets.UTF_8));
                        } else {
                            input.transferTo(zipOut);
                        }
                    }
                }
                zipOut.closeEntry();
            }
        }
    }

    static List<String> normalizePatchLines(List<String> lines) {
        var result = new ArrayList<String>(lines.size());
        var inHunk = false;
        for (var line : lines) {
            if (line.startsWith("@@")) {
                inHunk = true;
            }
            result.add(inHunk && line.isEmpty() ? " " : line);
        }
        return result;
    }

    @Override
    public void computeCacheKey(CacheKeyBuilder ck) {
        super.computeCacheKey(ck);
        ck.add("patches", patches.cacheKey());
        ck.add("patches folder", patches.folder());
    }
}
