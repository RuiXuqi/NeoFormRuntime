package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.actions.ExtensibleClasspath;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;

import java.util.List;

/** Applies the compile-only compatibility libraries required by Cleanroom userdev. */
public final class CleanroomRecompileClasspath {
    private static final String CLEANROOM_GROUP = "com.cleanroommc";
    private static final String CLEANROOM_ARTIFACT = "cleanroom";

    private CleanroomRecompileClasspath() {}

    public static void configureIfNeeded(String universalArtifact, ExtensibleClasspath classpath) {
        if (!universalArtifact.matches("[^:]+:[^:]+:[^:@]+(?::[^@]+)?(?:@[^:]+)?")) {
            return;
        }
        var universal = MavenCoordinate.parse(universalArtifact);
        if (!CLEANROOM_GROUP.equals(universal.groupId())
                || !CLEANROOM_ARTIFACT.equals(universal.artifactId())) {
            return;
        }

        classpath.excludeMinecraftLibraryGroup("org.lwjgl.lwjgl");
        classpath.excludeMinecraftLibraryGroup("oshi-project");
        classpath.excludeMinecraftLibraryGroup("net.java.jutils");
        classpath.excludeMinecraftLibrary("com.mojang", "patchy");
        classpath.excludeMinecraftLibrary("com.ibm.icu", "icu4j-core-mojang");
        classpath.excludeMinecraftLibrary("io.netty", "netty-all");
        classpath.excludeMinecraftLibrary("net.java.dev.jna", "platform");
        classpath.addMavenLibraries(List.of(MavenCoordinate.parse("com.cleanroommc:lwjglx:1.0.0")));
    }
}
