package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.utils.MavenCoordinate;

import java.util.List;

/**
 * Repository compatibility rules for Cleanroom userdev. Cleanroom publishes its own MCP toolchain
 * artifacts on its own Maven repositories, which must never be used to resolve official Forge artifacts.
 */
public final class CleanroomRepositories {
    public static final String CLEANROOM_RELEASES = "https://repo.cleanroommc.com/releases";
    public static final String CLEANROOM_SNAPSHOTS = "https://repo.cleanroommc.com/snapshots";
    public static final String ARCSEEKERS_RELEASES = "https://maven.arcseekers.com/releases";

    private CleanroomRepositories() {
    }

    public static boolean isCleanroomUserdev(List<String> artifactCoordinates) {
        for (var artifactCoordinate : artifactCoordinates) {
            MavenCoordinate coordinate;
            try {
                coordinate = MavenCoordinate.parse(artifactCoordinate);
            } catch (IllegalArgumentException ignored) {
                continue; // local file path
            }
            if ("com.cleanroommc".equals(coordinate.groupId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Must only be called when {@link #isCleanroomUserdev} returned true for the current userdev.
     */
    public static List<String> additionalRepositories() {
        return List.of(CLEANROOM_RELEASES, CLEANROOM_SNAPSHOTS, ARCSEEKERS_RELEASES);
    }
}
