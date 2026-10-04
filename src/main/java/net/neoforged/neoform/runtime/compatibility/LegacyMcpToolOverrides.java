package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.jetbrains.annotations.Nullable;

/**
 * Substitutes tools referenced by legacy MCP configs (1.12.2 era) with versions that work
 * on modern JDKs.
 */
public final class LegacyMcpToolOverrides {
    /**
     * SpecialSource 1.11.3 shipped with newer legacy mcp_config versions cannot read class files
     * produced by modern binarypatcher versions (its bundled ASM is too old). 1.11.6 supports them.
     */
    public static final MavenCoordinate SPECIAL_SOURCE_1_11_3 = MavenCoordinate.parse("net.md-5:SpecialSource:1.11.3:shaded");
    public static final MavenCoordinate SPECIAL_SOURCE_1_11_6 = MavenCoordinate.parse("net.md-5:SpecialSource:1.11.6:shaded");

    private LegacyMcpToolOverrides() {
    }

    /**
     * Returns the tool artifact override for the given function, or {@code null} if none applies.
     * Applies to both the legacy {@code version} field and individual {@code classpath} entries.
     */
    @Nullable
    public static MavenCoordinate getToolOverride(String functionId, MavenCoordinate toolArtifact) {
        if ("rename".equals(functionId) && toolArtifact.equals(SPECIAL_SOURCE_1_11_3)) {
            return SPECIAL_SOURCE_1_11_6;
        }
        return null;
    }
}
