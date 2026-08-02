package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.actions.ExtensibleClasspath;
import net.neoforged.neoform.runtime.artifacts.ClasspathItem;
import net.neoforged.neoform.runtime.manifests.MinecraftDownload;
import net.neoforged.neoform.runtime.manifests.MinecraftLibrary;
import net.neoforged.neoform.runtime.manifests.MinecraftVersionManifest;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CleanroomClasspathTest {
    @Test
    void usesModernUserdevLibrariesForCleanroomListLibraries() {
        var retained = minecraftLibrary("example:retained:1.0");
        var manifest = versionManifest(List.of(
                minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4"),
                minecraftLibrary("oshi-project:oshi-core:1.1"),
                minecraftLibrary("net.java.jutils:jutils:1.0.0"),
                minecraftLibrary("com.mojang:patchy:1.1"),
                minecraftLibrary("com.ibm.icu:icu4j-core-mojang:51.2"),
                minecraftLibrary("io.netty:netty-all:4.1.9.Final"),
                minecraftLibrary("net.java.dev.jna:platform:3.4.0"),
                retained));
        var classpath = new ExtensibleClasspath();

        CleanroomClasspath.configureListLibrariesIfNeeded(
                "com.cleanroommc:cleanroom:0.5.17-alpha:universal", userdevLibraries(), classpath);

        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(
                        ClasspathItem.of(retained),
                        ClasspathItem.of(MavenCoordinate.parse("io.netty:netty-common:4.2.15.Final")),
                        ClasspathItem.of(MavenCoordinate.parse("com.ibm.icu:icu4j:78.3")),
                        ClasspathItem.of(MavenCoordinate.parse("com.github.oshi:oshi-core-ffm:7.3.2")),
                        ClasspathItem.of(MavenCoordinate.parse("net.java.dev.jna:jna-platform:5.19.1")),
                        ClasspathItem.of(MavenCoordinate.parse("com.cleanroommc:lwjglxx:1.1.21")));
    }

    @Test
    void addsLwjglxOnlyOnceToCleanroomRecompileClasspath() {
        var retained = minecraftLibrary("example:retained:1.0");
        var manifest = versionManifest(List.of(
                minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4"),
                retained));
        var classpath = new ExtensibleClasspath();

        CleanroomClasspath.configureRecompileIfNeeded(
                "com.cleanroommc:cleanroom:0.5.17-alpha:universal", userdevLibraries(), classpath);
        CleanroomClasspath.configureRecompileIfNeeded(
                "com.cleanroommc:cleanroom:0.5.17-alpha:universal", userdevLibraries(), classpath);

        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(
                        ClasspathItem.of(retained),
                        ClasspathItem.of(MavenCoordinate.parse("com.cleanroommc:lwjglx:1.0.0")),
                        ClasspathItem.of(MavenCoordinate.parse("io.netty:netty-common:4.2.15.Final")),
                        ClasspathItem.of(MavenCoordinate.parse("com.ibm.icu:icu4j:78.3")),
                        ClasspathItem.of(MavenCoordinate.parse("com.github.oshi:oshi-core-ffm:7.3.2")),
                        ClasspathItem.of(MavenCoordinate.parse("net.java.dev.jna:jna-platform:5.19.1")),
                        ClasspathItem.of(MavenCoordinate.parse("com.cleanroommc:lwjglxx:1.1.21")));
    }

    @Test
    void leavesForgeListLibrariesClasspathUntouched() {
        var lwjgl2 = minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4");
        var manifest = versionManifest(List.of(lwjgl2));
        var classpath = new ExtensibleClasspath();

        CleanroomClasspath.configureListLibrariesIfNeeded(
                "net.minecraftforge:forge:1.12.2-14.23.5.2860:universal", userdevLibraries(), classpath);

        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(ClasspathItem.of(lwjgl2));
    }

    @Test
    void leavesForgeRecompileClasspathUntouched() {
        var lwjgl2 = minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4");
        var manifest = versionManifest(List.of(lwjgl2));
        var classpath = new ExtensibleClasspath();

        var configured = CleanroomClasspath.configureRecompileIfNeeded(
                "net.minecraftforge:forge:1.12.2-14.23.5.2860:universal", userdevLibraries(), classpath);

        assertThat(configured).isFalse();
        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(ClasspathItem.of(lwjgl2));
    }

    @Test
    void leavesLocalUniversalArtifactPathUntouched() {
        var retained = minecraftLibrary("example:retained:1.0");
        var manifest = versionManifest(List.of(retained));
        var classpath = new ExtensibleClasspath();

        var configured = CleanroomClasspath.configureRecompileIfNeeded(
                "C:\\temp\\neoforge-universal.jar", userdevLibraries(), classpath);

        assertThat(configured).isFalse();
        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(ClasspathItem.of(retained));
    }

    private static List<MavenCoordinate> userdevLibraries() {
        return List.of(
                MavenCoordinate.parse("io.netty:netty-common:4.2.15.Final"),
                MavenCoordinate.parse("com.ibm.icu:icu4j:78.3"),
                MavenCoordinate.parse("com.github.oshi:oshi-core-ffm:7.3.2"),
                MavenCoordinate.parse("net.java.dev.jna:jna-platform:5.19.1"),
                MavenCoordinate.parse("com.cleanroommc:lwjglxx:1.1.21"),
                MavenCoordinate.parse("org.lwjgl.lwjgl:lwjgl:2.9.4"),
                MavenCoordinate.parse("oshi-project:oshi-core:1.1"),
                MavenCoordinate.parse("net.java.jutils:jutils:1.0.0"),
                MavenCoordinate.parse("com.mojang:patchy:1.1"),
                MavenCoordinate.parse("com.ibm.icu:icu4j-core-mojang:51.2"),
                MavenCoordinate.parse("io.netty:netty-all:4.1.9.Final"),
                MavenCoordinate.parse("net.java.dev.jna:platform:3.4.0"),
                MavenCoordinate.parse("com.cleanroommc:lwjglx:0.9.0"));
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
}
