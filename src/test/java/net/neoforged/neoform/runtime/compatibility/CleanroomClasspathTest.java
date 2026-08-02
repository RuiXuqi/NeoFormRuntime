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
    void excludesObsoleteMinecraftLibrariesFromCleanroomListLibraries() {
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
                "com.cleanroommc:cleanroom:0.5.17-alpha:universal", classpath);

        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(ClasspathItem.of(retained));
    }

    @Test
    void addsLwjglxOnlyOnceToCleanroomRecompileClasspath() {
        var retained = minecraftLibrary("example:retained:1.0");
        var manifest = versionManifest(List.of(
                minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4"),
                retained));
        var classpath = new ExtensibleClasspath();

        CleanroomClasspath.configureRecompileIfNeeded(
                "com.cleanroommc:cleanroom:0.5.17-alpha:universal", classpath);
        CleanroomClasspath.configureRecompileIfNeeded(
                "com.cleanroommc:cleanroom:0.5.17-alpha:universal", classpath);

        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(
                        ClasspathItem.of(retained),
                        ClasspathItem.of(MavenCoordinate.parse("com.cleanroommc:lwjglx:1.0.0")));
    }

    @Test
    void leavesForgeListLibrariesClasspathUntouched() {
        var lwjgl2 = minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4");
        var manifest = versionManifest(List.of(lwjgl2));
        var classpath = new ExtensibleClasspath();

        CleanroomClasspath.configureListLibrariesIfNeeded(
                "net.minecraftforge:forge:1.12.2-14.23.5.2860:universal", classpath);

        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(ClasspathItem.of(lwjgl2));
    }

    @Test
    void leavesForgeRecompileClasspathUntouched() {
        var lwjgl2 = minecraftLibrary("org.lwjgl.lwjgl:lwjgl:2.9.4");
        var manifest = versionManifest(List.of(lwjgl2));
        var classpath = new ExtensibleClasspath();

        CleanroomClasspath.configureRecompileIfNeeded(
                "net.minecraftforge:forge:1.12.2-14.23.5.2860:universal", classpath);

        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(ClasspathItem.of(lwjgl2));
    }

    @Test
    void leavesLocalUniversalArtifactPathUntouched() {
        var retained = minecraftLibrary("example:retained:1.0");
        var manifest = versionManifest(List.of(retained));
        var classpath = new ExtensibleClasspath();

        CleanroomClasspath.configureRecompileIfNeeded(
                "C:\\temp\\neoforge-universal.jar", classpath);

        assertThat(classpath.mergeWithMinecraftLibraries(manifest).getEffectiveClasspath())
                .containsExactly(ClasspathItem.of(retained));
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
