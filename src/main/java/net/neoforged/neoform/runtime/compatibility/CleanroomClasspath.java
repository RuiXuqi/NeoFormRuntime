package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.actions.ExtensibleClasspath;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;

import java.util.Collection;
import java.util.List;

/**
 * Applies the classpath compatibility rules required by Cleanroom userdev.
 */
public final class CleanroomClasspath {
    private static final String CLEANROOM_GROUP = "com.cleanroommc";
    private static final String CLEANROOM_ARTIFACT = "cleanroom";
    private static final MavenCoordinate LWJGLX = MavenCoordinate.parse("com.cleanroommc:lwjglx:1.0.0");

    private CleanroomClasspath() {
    }

    public static void configureListLibrariesIfNeeded(
            String universalArtifact,
            Collection<MavenCoordinate> userdevLibraries,
            ExtensibleClasspath classpath) {
        if (!isCleanroomUniversalArtifact(universalArtifact)) {
            return;
        }

        excludeObsoleteMinecraftLibraries(classpath);
        addCleanroomUserdevLibraries(userdevLibraries, classpath);
    }

    public static boolean configureRecompileIfNeeded(
            String universalArtifact,
            Collection<MavenCoordinate> userdevLibraries,
            ExtensibleClasspath classpath) {
        if (!isCleanroomUniversalArtifact(universalArtifact)) {
            return false;
        }

        excludeObsoleteMinecraftLibraries(classpath);
        classpath.addMavenLibraries(List.of(LWJGLX));
        addCleanroomUserdevLibraries(userdevLibraries, classpath);
        return true;
    }

    private static boolean isCleanroomUniversalArtifact(String universalArtifact) {
        if (!universalArtifact.matches("[^:]+:[^:]+:[^:@]+(?::[^@]+)?(?:@[^:]+)?")) {
            return false;
        }
        var universal = MavenCoordinate.parse(universalArtifact);
        return CLEANROOM_GROUP.equals(universal.groupId())
                && CLEANROOM_ARTIFACT.equals(universal.artifactId());
    }

    private static void excludeObsoleteMinecraftLibraries(ExtensibleClasspath classpath) {
        classpath.excludeMinecraftLibraryGroup("org.lwjgl.lwjgl");
        classpath.excludeMinecraftLibraryGroup("oshi-project");
        classpath.excludeMinecraftLibraryGroup("net.java.jutils");
        classpath.excludeMinecraftLibrary("com.mojang", "patchy");
        classpath.excludeMinecraftLibrary("com.ibm.icu", "icu4j-core-mojang");
        classpath.excludeMinecraftLibrary("io.netty", "netty-all");
        classpath.excludeMinecraftLibrary("net.java.dev.jna", "platform");
    }

    private static void addCleanroomUserdevLibraries(
            Collection<MavenCoordinate> userdevLibraries, ExtensibleClasspath classpath) {
        classpath.addMavenLibraries(userdevLibraries.stream()
                .filter(library -> !isObsoleteLibrary(library))
                .filter(library -> !isLwjglx(library))
                .toList());
    }

    private static boolean isObsoleteLibrary(MavenCoordinate library) {
        return switch (library.groupId()) {
            case "org.lwjgl.lwjgl", "oshi-project", "net.java.jutils" -> true;
            case "com.mojang" -> library.artifactId().equals("patchy");
            case "com.ibm.icu" -> library.artifactId().equals("icu4j-core-mojang");
            case "io.netty" -> library.artifactId().equals("netty-all");
            case "net.java.dev.jna" -> library.artifactId().equals("platform");
            default -> false;
        };
    }

    private static boolean isLwjglx(MavenCoordinate library) {
        return CLEANROOM_GROUP.equals(library.groupId()) && "lwjglx".equals(library.artifactId());
    }
}
