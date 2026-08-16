package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.actions.ExtensibleClasspath;
import net.neoforged.neoform.runtime.artifacts.ClasspathItem;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyForgeClasspathTest {
    private static final String FORGE_1122_UNIVERSAL = "net.minecraftforge:forge:1.12.2-14.23.5.2860:universal";

    @Test
    void usesModernUserdevLibrariesForForgeListLibraries() {
        var classpath = new ExtensibleClasspath();

        LegacyForgeClasspath.configureListLibrariesIfNeeded(FORGE_1122_UNIVERSAL, userdevLibraries(), classpath);

        assertThat(classpath.getEffectiveClasspath()).containsExactlyElementsOf(normalizedLibraries());
    }

    @Test
    void addsLibrariesOnlyOnceToForgeRecompileClasspath() {
        var classpath = new ExtensibleClasspath();

        assertThat(LegacyForgeClasspath.configureRecompileIfNeeded(FORGE_1122_UNIVERSAL, userdevLibraries(), classpath))
                .isTrue();
        assertThat(LegacyForgeClasspath.configureRecompileIfNeeded(FORGE_1122_UNIVERSAL, userdevLibraries(), classpath))
                .isTrue();

        assertThat(classpath.getEffectiveClasspath()).containsExactlyElementsOf(normalizedLibraries());
    }

    @Test
    void leavesOtherForgeVersionsUntouched() {
        var classpath = new ExtensibleClasspath();

        LegacyForgeClasspath.configureListLibrariesIfNeeded(
                "net.minecraftforge:forge:1.12.1-14.22.1.2485:universal", userdevLibraries(), classpath);
        assertThat(LegacyForgeClasspath.configureRecompileIfNeeded(
                "net.minecraftforge:forge:1.12.1-14.22.1.2485:universal", userdevLibraries(), classpath))
                .isFalse();

        assertThat(classpath.getEffectiveClasspath()).isEmpty();
    }

    private static List<MavenCoordinate> userdevLibraries() {
        return List.of(
                MavenCoordinate.parse("org.scala-lang:scala-library:2.11.1"),
                MavenCoordinate.parse("org.scala-lang.plugins:scala-continuations-library_2.11:1.0.2_mc"),
                MavenCoordinate.parse("org.scala-lang.plugins:scala-continuations-plugin_2.11.1:1.0.2_mc"),
                MavenCoordinate.parse("org.scala-lang:scala-actors-migration_2.11:1.1.0"),
                MavenCoordinate.parse("org.scala-lang:scala-parser-combinators_2.11:1.0.1"),
                MavenCoordinate.parse("org.scala-lang:scala-swing_2.11:1.0.1"),
                MavenCoordinate.parse("org.scala-lang:scala-xml_2.11:1.0.2"),
                MavenCoordinate.parse("org.ow2.asm:asm:5.2"));
    }

    private static List<ClasspathItem> normalizedLibraries() {
        return List.of(
                ClasspathItem.of(MavenCoordinate.parse("org.scala-lang:scala-library:2.11.1")),
                ClasspathItem.of(MavenCoordinate.parse("org.scala-lang.modules:scala-parser-combinators_2.11:1.0.1")),
                ClasspathItem.of(MavenCoordinate.parse("org.scala-lang.modules:scala-swing_2.11:1.0.1")),
                ClasspathItem.of(MavenCoordinate.parse("org.scala-lang.modules:scala-xml_2.11:1.0.2")),
                ClasspathItem.of(MavenCoordinate.parse("org.ow2.asm:asm:5.2")));
    }
}
