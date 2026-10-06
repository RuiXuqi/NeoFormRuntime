package net.neoforged.neoform.runtime.cli;

import net.neoforged.neoform.runtime.actions.ApplySourceTransformAction;
import net.neoforged.neoform.runtime.actions.CreateLibrariesOptionsFile;
import net.neoforged.neoform.runtime.actions.ExternalJavaToolAction;
import net.neoforged.neoform.runtime.artifacts.ClasspathItem;
import net.neoforged.neoform.runtime.config.neoform.NeoFormFunction;
import net.neoforged.neoform.runtime.engine.NeoFormEngine;
import net.neoforged.neoform.runtime.engine.ProcessingEnvironment;
import net.neoforged.neoform.runtime.graph.ExecutionGraph;
import net.neoforged.neoform.runtime.graph.NodeInput;
import net.neoforged.neoform.runtime.graph.NodeOutput;
import net.neoforged.neoform.runtime.graph.NodeOutputType;
import net.neoforged.neoform.runtime.manifests.MinecraftDownload;
import net.neoforged.neoform.runtime.manifests.MinecraftLibrary;
import net.neoforged.neoform.runtime.manifests.MinecraftVersionManifest;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunNeoFormCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void configuresUserdevListLibrariesForAllExternalToolActions() {
        var graph = new ExecutionGraph();

        var decompileListLibraries = new CreateLibrariesOptionsFile();
        var decompileAction = new ExternalJavaToolAction(MavenCoordinate.parse("example:decompiler:1.0"));
        decompileAction.setListLibraries(decompileListLibraries);
        var decompileNode = graph.nodeBuilder("decompile");
        decompileNode.action(decompileAction);
        decompileNode.build();

        var transformSourcesAction = new ApplySourceTransformAction();
        var transformSourcesNode = graph.nodeBuilder("transformSources");
        transformSourcesNode.action(transformSourcesAction);
        transformSourcesNode.build();

        RunNeoFormCommand.configureUserdevListLibraries(
                graph,
                "com.cleanroommc:cleanroom:0.5.17-alpha:universal",
                List.of(
                        MavenCoordinate.parse("io.netty:netty-common:4.2.15.Final"),
                        MavenCoordinate.parse("org.lwjgl.lwjgl:lwjgl:2.9.4"),
                        MavenCoordinate.parse("com.cleanroommc:lwjglx:1.0.0")));

        var retained = minecraftLibrary("example:retained:1.0");
        var manifest = versionManifest(List.of(
                minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4"),
                retained));

        assertThat(decompileListLibraries.getClasspath()
                .mergeWithMinecraftLibraries(manifest)
                .getEffectiveClasspath())
                .containsExactly(
                        ClasspathItem.of(retained),
                        ClasspathItem.of(MavenCoordinate.parse("io.netty:netty-common:4.2.15.Final")));
        assertThat(transformSourcesAction.getListLibraries().getClasspath()
                .mergeWithMinecraftLibraries(manifest)
                .getEffectiveClasspath())
                .containsExactly(
                        ClasspathItem.of(retained),
                        ClasspathItem.of(MavenCoordinate.parse("io.netty:netty-common:4.2.15.Final")));
    }

    @Test
    void userdevSourceProcessorToolOverrideIsAppliedThroughEngine() throws Exception {
        // The CLI used to assemble the source processor classpath itself and miss the legacy
        // tool overrides; going through the engine must yield the overridden artifact.
        var graph = new ExecutionGraph();
        var inputNode = graph.nodeBuilder("patch");
        inputNode.action(new ExternalJavaToolAction(MavenCoordinate.parse("test:tool:1.0")));
        var inputOutput = inputNode.output("output", NodeOutputType.ZIP, "input");
        inputNode.build();

        var engine = new NeoFormEngine(
                null, null, null, null);
        try {
            var builder = graph.nodeBuilder("processForgeSources");
            builder.input("input", inputOutput.asInput());
            var function = new NeoFormFunction(
                    "net.md-5:SpecialSource:1.11.3:shaded",
                    null,
                    null,
                    null,
                    List.of("--input", "{input}", "--output", "{output}"),
                    null);

            var output = engine.applyFunctionToNode("rename", Map.of(), List.of(), function, builder);

            assertThat(output).isPresent();
            var action = (ExternalJavaToolAction) builder.build().action();
            assertThat(action.getClasspath())
                    .extracting(MavenCoordinate::toString)
                    .containsExactly("net.md-5:SpecialSource:1.11.6:shaded");
            assertThat(action.getArgs()).contains("{input}", "{output}");
        } finally {
            engine.close();
        }
    }

    @Test
    void legacyUniversalMetadataInjectionInjectsResourcesWithoutClasses() throws Exception {
        var recompiledJar = tempDir.resolve("recompiled.jar");
        writeZip(recompiledJar, List.of(
                new TestEntry("example/Recompiled.class", "recompiled")
        ));

        var universalJar = tempDir.resolve("universal.jar");
        writeZip(universalJar, List.of(
                new TestEntry("META-INF/services/example.Service", "example.ServiceProvider\n"),
                new TestEntry("META-INF/EXAMPLE.SF", "signature"),
                new TestEntry("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n"),
                new TestEntry("example/Recompiled.class", "universal replacement"),
                new TestEntry("example/Universal.class", "universal"),
                new TestEntry("assets/example/icon.png", "image")
        ));

        var outputJar = tempDir.resolve("output.jar");
        var environment = mock(ProcessingEnvironment.class);
        when(environment.getRequiredInputPath("input")).thenReturn(recompiledJar);
        when(environment.getOutputPath("output")).thenReturn(outputJar);

        try (var universalZip = new ZipFile(universalJar.toFile())) {
            var action = RunNeoFormCommand.createLegacyUniversalMetadataInjection(
                    universalZip,
                    List.of("^(?!excluded\\.txt$).*")
            );
            action.run(environment);
        }

        try (var output = new ZipFile(outputJar.toFile())) {
            assertThat(readEntry(output, "META-INF/services/example.Service"))
                    .isEqualTo("example.ServiceProvider\n");
            assertThat(readEntry(output, "example/Recompiled.class")).isEqualTo("recompiled");
            assertThat(output.getEntry("example/Universal.class")).isNull();
            assertThat(output.getEntry("META-INF/EXAMPLE.SF")).isNull();
            assertThat(readEntry(output, "META-INF/MANIFEST.MF")).isEqualTo("Manifest-Version: 1.0\n");
            assertThat(readEntry(output, "assets/example/icon.png")).isEqualTo("image");
        }
    }

    private static void writeZip(Path path, List<TestEntry> entries) throws IOException {
        try (var output = new ZipOutputStream(Files.newOutputStream(path))) {
            for (var entry : entries) {
                output.putNextEntry(new ZipEntry(entry.name()));
                output.write(entry.content().getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static String readEntry(ZipFile zip, String name) throws IOException {
        var entry = zip.getEntry(name);
        assertThat(entry).isNotNull();
        try (var input = zip.getInputStream(entry)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static MinecraftVersionManifest versionManifest(List<MinecraftLibrary> libraries) {
        return new MinecraftVersionManifest("test", Map.of(), libraries, null, null, null, null, null);
    }

    private static MinecraftLibrary minecraftLibrary(String coordinate) {
        return new MinecraftLibrary(
                coordinate,
                new MinecraftLibrary.Downloads(new MinecraftDownload("", 0, null, null), Map.of()),
                List.of(),
                null);
    }

    private record TestEntry(String name, String content) {
    }
}
