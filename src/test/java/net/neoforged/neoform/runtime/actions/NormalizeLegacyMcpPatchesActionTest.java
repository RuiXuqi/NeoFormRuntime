package net.neoforged.neoform.runtime.actions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizeLegacyMcpPatchesActionTest {
    @Test
    void prefixesBareBlankLinesInsideHunksOnly() {
        var lines = List.of(
                "--- a/example.java",
                "+++ b/example.java",
                "",
                "@@ -1,4 +1,4 @@",
                " context",
                "",
                "-old",
                "+new",
                " ",
                "@@ -8,2 +8,2 @@",
                "",
                "-before",
                "+after"
        );

        assertThat(NormalizeLegacyMcpPatchesAction.normalizePatchLines(lines)).containsExactly(
                "--- a/example.java",
                "+++ b/example.java",
                "",
                "@@ -1,4 +1,4 @@",
                " context",
                " ",
                "-old",
                "+new",
                " ",
                "@@ -8,2 +8,2 @@",
                " ",
                "-before",
                "+after"
        );
    }
}
