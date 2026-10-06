package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyMcpToolOverridesTest {
    @Test
    void overridesSpecialSource1113() {
        assertThat(LegacyMcpToolOverrides.getToolOverride("rename", LegacyMcpToolOverrides.SPECIAL_SOURCE_1_11_3))
                .isEqualTo(LegacyMcpToolOverrides.SPECIAL_SOURCE_1_11_6);
    }

    @Test
    void doesNotOverrideOtherToolsOrVersions() {
        assertThat(LegacyMcpToolOverrides.getToolOverride("decompile", LegacyMcpToolOverrides.SPECIAL_SOURCE_1_11_3))
                .isNull();
        assertThat(LegacyMcpToolOverrides.getToolOverride("rename", LegacyMcpToolOverrides.SPECIAL_SOURCE_1_11_6))
                .isNull();
        assertThat(LegacyMcpToolOverrides.getToolOverride("rename", MavenCoordinate.parse("net.md-5:SpecialSource:1.8.3:shaded")))
                .isNull();
    }
}
