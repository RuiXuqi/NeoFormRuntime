package net.neoforged.neoform.runtime.graph;

import net.neoforged.neoform.runtime.actions.ApplyDevTransformsAction;
import net.neoforged.neoform.runtime.actions.ExternalJavaToolAction;
import net.neoforged.neoform.runtime.actions.InjectFromZipFileSource;
import net.neoforged.neoform.runtime.actions.InjectZipContentAction;
import net.neoforged.neoform.runtime.cli.Main;
import net.neoforged.neoform.runtime.cli.ResultIds;
import net.neoforged.neoform.runtime.cli.RunNeoFormCommand;
import net.neoforged.neoform.runtime.engine.NeoFormEngine;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Regression tests for the structure of the graph when building NeoForm.
 */
public class NeoFormGraphTest {

    @Nested
    class NeoForm_1_21 {
        static ExecutionGraph graph = buildGraph("--neoform", "net.neoforged:neoform:1.21-20240613.152323@zip");

        @Test
        void testBinaryPatchProcess() {
            assertThat(graph.getNodes()).extracting("id").doesNotContain("applyDevTransforms");

            // No Recompile Pipeline
            assertResultFromNode(graph, "rename", "output", ResultIds.GAME_JAR_NO_RECOMP);
            assertResultFromNode(graph, "rename", "output", ResultIds.VANILLA_DEOBFUSCATED);
        }

        @Test
        void testDecompilerClasspath() {
            var action = (ExternalJavaToolAction) graph.getRequiredNode("decompile").action();
            assertThat(action.getClasspath())
                    .extracting(MavenCoordinate::toString)
                    .containsExactly("org.vineflower:vineflower:1.10.1");
            assertNull(action.getMainClass());
        }
    }

    @Test
    void testNeoForm_1_21_WithDevTransforms() throws Exception {
        var graph = buildGraph("--neoform", "net.neoforged:neoform:1.21-20240613.152323@zip", "--access-transformer", "at.cfg");
        assertNotPredecessor(graph, "applyDevTransforms", "decompile");

        // No Recompile Pipeline
        assertNodeChain(graph, "rename", "applyDevTransforms");
        assertResultFromNode(graph, "applyDevTransforms", "output", ResultIds.GAME_JAR_NO_RECOMP);
        assertResultFromNode(graph, "rename", "output", ResultIds.VANILLA_DEOBFUSCATED);
    }

    @Nested
    class NeoForge_1_21 {
        static ExecutionGraph graph = buildGraph("--neoforge", "net.neoforged:neoforge:21.0.0-beta:userdev");

        @Test
        void testBinaryPatchProcess() {
            // No Recompile Pipeline
            assertNodeChain(graph, "rename", "binaryPatch", "copyUnpatchedClasses", "applyDevTransforms", "binaryWithNeoForge");
            assertResultFromNode(graph, "applyDevTransforms", "output", ResultIds.GAME_JAR_NO_RECOMP);
            assertResultFromNode(graph, "binaryWithNeoForge", "output", ResultIds.GAME_JAR_NO_RECOMP_WITH_NEOFORGE);
            assertResultFromNode(graph, "rename", "output", ResultIds.VANILLA_DEOBFUSCATED);
        }

        @Test
        void testNeoForgeAccessTransformersAreAppliedToNoRecompArtifacts() {
            var action = (ApplyDevTransformsAction) graph.getRequiredNode("applyDevTransforms").action();
            assertThat(action.getAccessTransformersData()).containsOnly("neoForgeAccessTransformers");
        }
    }

    @Test
    void testMCP_1_20_1() throws Exception {
        var graph = buildGraph("--neoform", "de.oceanlabs.mcp:mcp_config:1.20.1@zip");
        assertThat(graph.getNodes()).extracting("id").doesNotContain("applyDevTransforms");
        var patchAction = (ExternalJavaToolAction) graph.getRequiredNode("patch").action();
        assertThat(patchAction.getArgs())
                .contains("{patches}")
                .doesNotContain("--prefix");

        // No Recompile Pipeline
        assertNodeChain(graph, "rename", "remapClassesToNamed");
        assertResultFromNode(graph, "remapClassesToNamed", "output", ResultIds.GAME_JAR_NO_RECOMP);
        assertResultFromNode(graph, "rename", "output", ResultIds.VANILLA_DEOBFUSCATED);
    }

    @Test
    void testMCP_1_12_2_NormalizesLegacyPatches() {
        var graph = buildGraph("--neoform", "de.oceanlabs.mcp:mcp_config:1.12.2@zip");

        assertNodeChain(graph, "normalizeLegacyMcpPatches", "patch");
        var patchAction = (ExternalJavaToolAction) graph.getRequiredNode("patch").action();
        assertThat(patchAction.getArgs())
                .contains("{patches}")
                .containsSubsequence("--prefix", "patches/joined/")
                .containsSubsequence("--mode", "FUZZY");
    }

    @Test
    void testMCP_1_12_2_ExposesSrgToMcpTsrgMapping() {
        var graph = buildGraph("--neoform", "de.oceanlabs.mcp:mcp_config:1.12.2@zip");

        assertResultFromNode(graph, "createMcpMappings", "srgToMcpTsrg", ResultIds.INTERMEDIARY_TO_NAMED_MAPPING_TSRG);
    }

    @Test
    void testMCP_1_12_2_ExposesMcpToSrgMappingsInBothFormats() {
        var graph = buildGraph("--neoform", "de.oceanlabs.mcp:mcp_config:1.12.2@zip");

        assertResultFromNode(graph, "createMcpMappings", "mcpToSrgTsrg", ResultIds.NAMED_TO_INTERMEDIARY_MAPPING);
        assertResultFromNode(graph, "createMcpMappings", "mcpToSrg", ResultIds.NAMED_TO_INTERMEDIARY_MAPPING_SRG);
    }

    @Test
    void testMCP_1_12_2_SourceChainKeepsSrgUntilPatched() {
        var graph = buildGraph("--neoform", "de.oceanlabs.mcp:mcp_config:1.12.2@zip");

        // 1.12.2 MCP patches are written against SRG names, so sources keep SRG names until
        // applyMcpCsvData (javadoc/params) and remapSourcesToNamed (names) after patching.
        assertNodeChain(graph, "rename", "mcinject", "decompile", "inject", "patch", "applyMcpCsvData", "remapSourcesToNamed");
        // The classes remap to MCP happens on the NeoForge-combined jar instead
        assertResultFromNode(graph, "mcinject", "output", ResultIds.VANILLA_DEOBFUSCATED);
    }

    @Test
    void testMCP_1_12_2_RegistersAllMcpMappingResults() {
        var graph = buildGraph("--neoform", "de.oceanlabs.mcp:mcp_config:1.12.2@zip");

        graph.getRequiredNode("createMcpMappings");
        assertResultFromNode(graph, "createMcpMappings", "mcpToSrgTsrg", ResultIds.NAMED_TO_INTERMEDIARY_MAPPING);
        assertResultFromNode(graph, "createMcpMappings", "mcpToSrg", ResultIds.NAMED_TO_INTERMEDIARY_MAPPING_SRG);
        assertResultFromNode(graph, "createMcpMappings", "srgToMcp", ResultIds.INTERMEDIARY_TO_NAMED_MAPPING);
        assertResultFromNode(graph, "createMcpMappings", "srgToMcpTsrg", ResultIds.INTERMEDIARY_TO_NAMED_MAPPING_TSRG);
        assertResultFromNode(graph, "createMcpMappings", "srgToMcpTsrg2", ResultIds.INTERMEDIARY_TO_NAMED_MAPPING_TSRG2);
        assertResultFromNode(graph, "createMcpMappings", "csvMappings", ResultIds.CSV_MAPPING);
        assertResultFromNode(graph, "createMcpMappings", "notchToSrg", ResultIds.NOTCH_TO_INTERMEDIARY_MAPPING);
    }

    @Test
    void testMCP_1_15_2_SourceChainAppliesCsvDataBeforeRemap() {
        var graph = buildGraph("--neoform", "de.oceanlabs.mcp:mcp_config:1.15.2@zip", "--mcp-mappings", "de.oceanlabs.mcp:mcp_stable:60-1.15@zip");

        // The MCP CSV data (javadoc/params) is keyed by SRG names, so it must be applied to the
        // patched sources while they still use SRG names, before the SRG -> MCP source remap.
        assertNodeChain(graph, "rename", "decompile", "inject", "patch", "applyMcpCsvData", "remapSourcesToNamed");
        // Unlike 1.12.2, the class remap is part of the engine-built graph for this version range
        assertResultFromNode(graph, "remapClassesToNamed", "output", ResultIds.GAME_JAR_NO_RECOMP);
    }

    @Test
    void testMCP_1_20_1_UsesIntermediaryPipeline() throws Exception {
        // Guard rail for the INTERMEDIARY naming scheme (1.17-1.20.1): sources and classes are
        // remapped from SRG to official names via Mojang's ProGuard mappings.
        var graph = buildGraph("--neoform", "de.oceanlabs.mcp:mcp_config:1.20.1@zip");

        assertNodeChain(graph, "patch", "remapSourcesToNamed");
        assertNodeChain(graph, "rename", "remapClassesToNamed");
        assertResultFromNode(graph, "remapClassesToNamed", "output", ResultIds.GAME_JAR_NO_RECOMP);
    }

    @Test
    void testMCP_1_20_1_WithDevTransforms() throws Exception {
        var graph = buildGraph("--neoform", "de.oceanlabs.mcp:mcp_config:1.20.1@zip", "--access-transformer", "at.cfg");
        assertNotPredecessor(graph, "applyDevTransforms", "decompile");

        // No Recompile Pipeline
        assertNodeChain(graph, "rename", "applyDevTransforms", "remapClassesToNamed");
        assertResultFromNode(graph, "remapClassesToNamed", "output", ResultIds.GAME_JAR_NO_RECOMP);
        assertResultFromNode(graph, "rename", "output", ResultIds.VANILLA_DEOBFUSCATED);
    }

    @Test
    void testNeoForge_1_20_1() throws Exception {
        var graph = buildGraph("--neoforge", "net.neoforged:forge:1.20.1-47.1.54:userdev");

        // Recompile Pipeline
        assertNodeChain(graph, "recompile", "compiledWithNeoForge");
        assertResultFromNode(graph, "compiledWithNeoForge", "output", ResultIds.GAME_JAR_WITH_NEOFORGE);

        // No Recompile Pipeline
        assertNodeChain(graph, "rename", "binaryPatch", "copyUnpatchedClasses", "applyDevTransforms", "binaryWithNeoForge", "remapClassesToNamed");
        assertResultFromNode(graph, "remapClassesToNamed", "output", ResultIds.GAME_JAR_NO_RECOMP);
        assertResultFromNode(graph, "remapClassesToNamed", "output", ResultIds.GAME_JAR_NO_RECOMP_WITH_NEOFORGE);
        assertResultFromNode(graph, "rename", "output", ResultIds.VANILLA_DEOBFUSCATED);
    }

    @Test
    void testMCP_1_12_2_WithForgeInjectsUniversalResourcesOnly() throws Exception {
        var graph = buildGraph("--add-repository", "https://maven.minecraftforge.net", "--neoforge", "net.minecraftforge:forge:1.12.2-14.23.5.2860:userdev3");

        // Legacy MCP compiles the Forge sources itself, so the universal jar contributes
        // everything except classes and signature files
        var action = (InjectZipContentAction) graph.getRequiredNode("compiledWithNeoForge").action();
        var source = (InjectFromZipFileSource) action.getInjectedSources().getFirst();
        assertThat(source.includeFilterPattern().pattern())
                .contains(".*\\.class$")
                .contains("META-INF/")
                .doesNotContain("(?=META-INF/services/");
    }

    @Test
    void testMCP_1_12_2_WithForgeExcludesBinpatchesFromBinary() throws Exception {
        var graph = buildGraph("--add-repository", "https://maven.minecraftforge.net", "--neoforge", "net.minecraftforge:forge:1.12.2-14.23.5.2860:userdev3");

        // FML applies binpatches.pack.lzma from the classpath at runtime, so the dev jar must not contain it
        var action = (InjectZipContentAction) graph.getRequiredNode("binaryWithNeoForge").action();
        var source = (InjectFromZipFileSource) action.getInjectedSources().getFirst();
        assertThat(source.includeFilterPattern().pattern())
                .contains("binpatches\\.pack\\.lzma");
    }

    @Test
    void testMCP_1_12_2_BinaryPatchRenameUsesCompatibleSpecialSource() throws Exception {
        var graph = buildGraph("--add-repository", "https://maven.minecraftforge.net", "--neoforge", "net.minecraftforge:forge:1.12.2-14.23.5.2860:userdev3");

        // binaryPatchRename clones the rename function; SpecialSource 1.8.3 is too old for modern class files
        var action = (ExternalJavaToolAction) graph.getRequiredNode("binaryPatchRename").action();
        assertThat(action.getClasspath())
                .extracting(MavenCoordinate::toString)
                .containsExactly("net.md-5:SpecialSource:1.8.3:shaded");
    }

    @Test
    void testCleanroom_BinaryPatchRenameUsesOverriddenSpecialSource() throws Exception {
        var graph = buildGraph(
                "--add-repository", "https://repo.cleanroommc.com/releases",
                "--add-repository", "https://repo.cleanroommc.com/snapshots",
                "--add-repository", "https://maven.arcseekers.com/releases",
                "--neoforge", "com.cleanroommc:cleanroom:0.6.13-alpha:userdev");

        // Cleanroom's mcp_config references SpecialSource 1.11.3, which cannot read modern class files
        var action = (ExternalJavaToolAction) graph.getRequiredNode("binaryPatchRename").action();
        assertThat(action.getClasspath())
                .extracting(MavenCoordinate::toString)
                .containsExactly("net.md-5:SpecialSource:1.11.6:shaded");
    }

    @Test
    void testMCP_1_12_2_WithForgeSourceProcessorGoesThroughEngineFunctionNode() throws Exception {
        var graph = buildGraph(
                "--add-repository", "https://maven.minecraftforge.net",
                "--neoforge", "net.minecraftforge:forge:1.12.2-14.23.5.2860:userdev3");

        // The CLI used to duplicate the engine's function classpath assembly and silently missed
        // the legacy MCP tool overrides; the source processor node must now be built by the engine.
        var processorNode = graph.getNode("processForgeSources");
        assertNotNull(processorNode, "Expected a processForgeSources node for 1.12.2 userdev");
        var action = (ExternalJavaToolAction) processorNode.action();
        assertThat(action.getArgs()).contains("{input}", "{output}");
        assertThat(action.getClasspath())
                .extracting(MavenCoordinate::toString)
                .containsExactly("net.minecraftforge:mcpcleanup:2.3.2:fatjar");
    }

    private static void assertResultFromNode(ExecutionGraph graph, String nodeId, String outputId, String resultId) {
        var output = graph.getResult(resultId);
        assertEquals(nodeId, output.getNode().id(), "Expected result " + resultId + " to be from node " + nodeId);
        assertEquals(outputId, output.id(), "Expected result of " + nodeId + " to be from output " + outputId);
    }

    /**
     * Asserts that the given nodes form a succession in the graph.
     */
    private static void assertNodeChain(ExecutionGraph graph, String... chainedNodeIds) {
        for (int i = chainedNodeIds.length - 1; i > 0; i--) {
            var nodeId = chainedNodeIds[i];
            assertPredecessor(graph, chainedNodeIds[i - 1], nodeId);
        }
        if (chainedNodeIds.length > 0) {
            graph.getRequiredNode(chainedNodeIds[0]);
        }
    }

    private static Set<ExecutionNode> getPredecessors(ExecutionGraph graph, String nodeId) {
        Set<ExecutionNode> result = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ExecutionNode> openSet = new ArrayList<>();
        openSet.add(graph.getRequiredNode(nodeId));
        while (!openSet.isEmpty()) {
            var node = openSet.removeLast();
            for (var entry : node.inputs().entrySet()) {
                for (var nodeDependency : entry.getValue().getNodeDependencies()) {
                    if (result.add(nodeDependency)) {
                        openSet.add(nodeDependency);
                    }
                }
            }
        }
        return result;
    }

    private static void assertPredecessor(ExecutionGraph graph, String nodeId, String otherNodeId) {
        assertThat(getPredecessors(graph, otherNodeId))
                .as("Expected %s to be in the set of transitive predecessor nodes of %s", nodeId, otherNodeId)
                .extracting("id").contains(nodeId);
    }

    private static void assertNotPredecessor(ExecutionGraph graph, String nodeId, String otherNodeId) {
        assertNotPredecessor(graph, nodeId, otherNodeId, new LinkedHashSet<>());
    }

    private static void assertNotPredecessor(ExecutionGraph graph, String nodeId, String otherNodeId, Set<String> visitedNodes) {
        if (visitedNodes.contains(otherNodeId)) {
            fail("Cycle in graph: " + String.join(" -> ", visitedNodes) + " -> " + otherNodeId);
        }

        var node1 = graph.getRequiredNode(nodeId);
        var node2 = graph.getRequiredNode(otherNodeId);

        visitedNodes.add(node2.id());
        for (var entry : node2.inputs().entrySet()) {
            for (ExecutionNode nodeDependency : entry.getValue().getNodeDependencies()) {
                if (nodeDependency == node1) {
                    fail(node1 + " is predecessor of " + node2 + " along chain " + String.join(" -> ", visitedNodes));
                }
                assertNotPredecessor(graph, nodeId, nodeDependency.id(), visitedNodes);
            }
        }
        visitedNodes.remove(node2.id());
    }

    private static ExecutionGraph buildGraph(String... args) {
        var fullArgs = new ArrayList<String>();
        Collections.addAll(fullArgs, "run", "--print-graph");
        Collections.addAll(fullArgs, args);

        var graphHolder = new AtomicReference<ExecutionGraph>();
        var command = new RunNeoFormCommand() {
            @Override
            protected void runWithNeoFormEngine(NeoFormEngine engine, List<AutoCloseable> closables) throws IOException, InterruptedException {
                super.runWithNeoFormEngine(engine, closables);
                graphHolder.set(engine.getGraph()); // Capture the graph.
            }
        };
        var defaultFactory = CommandLine.defaultFactory();
        var commandLine = new CommandLine(new Main(), new CommandLine.IFactory() {
            @Override
            public <K> K create(Class<K> cls) throws Exception {
                if (RunNeoFormCommand.class.isAssignableFrom(cls)) {
                    return cls.cast(command);
                }
                return defaultFactory.create(cls);
            }
        });
        commandLine.parseArgs(fullArgs.toArray(String[]::new));
        assertEquals(0, commandLine.execute(fullArgs.toArray(String[]::new)));

        var graph = graphHolder.get();
        assertNotNull(graph);
        return graph;
    }

}
