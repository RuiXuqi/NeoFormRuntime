package net.neoforged.neoform.runtime.actions;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyMcpCsvDataActionTest {

    @Test
    void multiLineJavadocWithLiteralLineBreaksIsExpanded() {
        var docs = new ApplyMcpCsvDataAction.ClassDocs(
                Map.of(),
                Map.of("isTopSolid", "Determines if the block is solid enough.\n@deprecated prefer calling {@link IBlockState#isTopSolid()}"));

        var source = "public class Block {\n"
                + "    @Deprecated\n"
                + "    public boolean isTopSolid(IBlockState state)\n"
                + "    {\n"
                + "        return true;\n"
                + "    }\n"
                + "}\n";

        var result = ApplyMcpCsvDataAction.applyToSource(source, docs);

        assertThat(result).contains(
                "/**",
                " * Determines if the block is solid enough.",
                " * @deprecated prefer calling {@link IBlockState#isTopSolid()}",
                " */");
        assertThat(result).doesNotContain("\\n"); // literal backslash-n
    }

    @Test
    void singleLineJavadocStaysOnOneLine() {
        var docs = new ApplyMcpCsvDataAction.ClassDocs(
                Map.of("lightValue", "Amount of light emitted"),
                Map.of());

        var source = "public class Block {\n    protected int lightValue;\n}\n";

        var result = ApplyMcpCsvDataAction.applyToSource(source, docs);

        assertThat(result).contains("    /** Amount of light emitted */\n");
    }
}
