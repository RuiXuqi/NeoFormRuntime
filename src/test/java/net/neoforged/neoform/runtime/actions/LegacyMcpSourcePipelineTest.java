package net.neoforged.neoform.runtime.actions;

import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.srgutils.IMappingBuilder;
import net.neoforged.srgutils.IMappingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Behavior tests for the legacy MCP source pipeline: the CSV data (javadoc and parameter names)
 * must be applied while the sources still use SRG names, and the SRG to MCP remap must run last.
 * These tests pin the pipeline contract of the legacy MCP paths.
 */
class LegacyMcpSourcePipelineTest {

    private static final String SRG_SOURCE = """
            package net.minecraft.block;
            public class Block {
                public void func_176223_d() {}
                public boolean func_149686_d(Block p_i1234_1_) { return true; }
                public void onBlockAdded(Block p_i1234_1_) {}
            }
            """;

    @TempDir
    Path tempDir;

    /**
     * Patch-time source state (SRG names present) -> applyMcpCsvData -> remapSourcesToNamed.
     * This is the order both legacy MCP paths must use.
     */
    @Test
    void csvDataThenRemapProducesMcpSourcesWithJavadocAndParams() throws IOException {
        var pipeline = new Pipeline(tempDir);
        var csvResult = pipeline.applyCsvData(SRG_SOURCE);
        var finalSource = pipeline.remapSrgToMcp(csvResult);

        // Members carry MCP names; no SRG remnants
        assertThat(finalSource).contains("doTheThing", "isSolid");
        assertThat(finalSource).doesNotContain("func_176223_d", "func_149686_d");
        // CSV javadoc is present in the produced sources
        assertThat(finalSource).contains("/** Does the thing */");
        // CSV parameter names are applied even though the remap action does not touch p_* names
        assertThat(finalSource).contains("Block blockIn");
        assertThat(finalSource).doesNotContain("p_i1234_1_");
    }

    /**
     * The CSV docs are keyed by SRG names (see {@link ApplyMcpCsvDataAction} buildDocsByClass),
     * so applying the CSV data after the SRG -> MCP remap cannot find any member docs.
     * This is the historical 1.13-1.16.5 ordering and loses all javadocs.
     */
    @Test
    void remapBeforeCsvDataLosesJavadoc() throws IOException {
        var pipeline = new Pipeline(tempDir);
        var remapResult = pipeline.remapSrgToMcp(SRG_SOURCE);
        var finalSource = pipeline.applyCsvData(remapResult);

        // Names are still remapped
        assertThat(finalSource).contains("doTheThing");
        // ... but the doc lookup misses because the sources no longer contain the SRG names
        assertThat(finalSource).doesNotContain("Does the thing");
        // Parameter replacement uses p_* names, which the source remap never touches
        assertThat(finalSource).contains("Block blockIn");
    }

    /**
     * Docs keyed by SRG names must not match a member that was already renamed to its MCP name,
     * and parameter replacement must leave already-aliased (non p_*) names untouched.
     */
    @Test
    void csvDataIsKeyedBySrgNamesAndLeavesNonParamNamesAlone() {
        var docs = new ApplyMcpCsvDataAction.ClassDocs(
                Map.of(),
                Map.of("func_176223_d", "Does the thing"));
        var params = Map.of("p_i1234_1_", "blockIn");

        var result = ApplyMcpCsvDataAction.applyToSource(
                "public class Block {\n"
                        + "    public void doTheThing() {}\n"
                        + "    public boolean isSolid(Block p_i1234_1_) { return true; }\n"
                        + "    public void onBlockAdded(Block p_i1234_1_) {}\n"
                        + "}\n",
                docs,
                params);

        assertThat(result).doesNotContain("/**");
        assertThat(result).contains("Block blockIn");
        assertThat(result).contains("onBlockAdded(Block blockIn)");
        assertThat(result).doesNotContain("p_i1234_1_");
    }

    /**
     * Builds the minimal artifacts the two actions need (an SRG mapping file and CSV files) and
     * runs them directly instead of standing up a full execution graph.
     */
    private static final class Pipeline {
        private final Path tempDir;

        Pipeline(Path tempDir) {
            this.tempDir = tempDir;
        }

        String applyCsvData(String source) throws IOException {
            var docs = new ApplyMcpCsvDataAction.ClassDocs(
                    Map.of(),
                    Map.of("func_176223_d", "Does the thing"));
            return ApplyMcpCsvDataAction.applyToSource(source, docs, Map.of("p_i1234_1_", "blockIn"));
        }

        String remapSrgToMcp(String source) throws IOException {
            var mappings = Map.of(
                    "func_176223_d", "doTheThing",
                    "func_149686_d", "isSolid");
            var mappingsFile = writeSrgMappings(tempDir.resolve("srg-to-mcp.srg"), mappings);
            var sourcesZip = tempDir.resolve("sources-" + System.nanoTime() + ".zip");
            var outputZip = tempDir.resolve("output-" + System.nanoTime() + ".zip");
            writeSourcesZip(sourcesZip, source);

            var environment = mock(ProcessingEnvironment.class);
            when(environment.getInputPath("mappings")).thenReturn(mappingsFile);
            when(environment.getRequiredInputPath("sources")).thenReturn(sourcesZip);
            when(environment.getOutputPath("output")).thenReturn(outputZip);

            var action = new RemapSrgSourcesAction();
            try {
                action.run(environment);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            return readSourceFromZip(outputZip);
        }

        private static Path writeSrgMappings(Path target, Map<String, String> srgToMcp) throws IOException {
            var builder = IMappingBuilder.create("srg", "mcp");
            var mappedClass = builder.addClass("net/minecraft/block/Block", "net/minecraft/block/Block");
            for (var entry : srgToMcp.entrySet()) {
                mappedClass.method("()V", entry.getKey(), entry.getValue());
            }
            builder.build().getMap("srg", "mcp").write(target, IMappingFile.Format.SRG, false);
            return target;
        }

        private static void writeSourcesZip(Path target, String source) throws IOException {
            try (var zipOut = new ZipOutputStream(Files.newOutputStream(target))) {
                zipOut.putNextEntry(new ZipEntry("net/minecraft/block/Block.java"));
                zipOut.write(source.getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();
            }
        }

        private static String readSourceFromZip(Path zip) throws IOException {
            try (var zipIn = new ZipInputStream(Files.newInputStream(zip))) {
                for (var entry = zipIn.getNextEntry(); entry != null; entry = zipIn.getNextEntry()) {
                    if (entry.getName().endsWith(".java")) {
                        return new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
            }
            throw new IllegalStateException("No .java entry found in " + zip);
        }
    }
}
