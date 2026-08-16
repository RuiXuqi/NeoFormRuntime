package net.neoforged.neoform.runtime.compatibility;

import net.neoforged.neoform.runtime.actions.ExtensibleClasspath;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;

import java.util.Collection;
import java.util.Objects;

/**Applies the classpath compatibility rules required by Forge 1.12.2 userdev3.*/
public final class LegacyForgeClasspath {
    private static final String FORGE_GROUP = "net.minecraftforge";
    private static final String FORGE_ARTIFACT = "forge";

    private LegacyForgeClasspath() {
    }

    public static void configureListLibrariesIfNeeded(
            String universalArtifact,
            Collection<MavenCoordinate> userdevLibraries,
            ExtensibleClasspath classpath) {
        if (isForge1122UniversalArtifact(universalArtifact)) {
            classpath.addMavenLibraries(normalizeLibraries(userdevLibraries));
        }
    }

    public static boolean configureRecompileIfNeeded(
            String universalArtifact,
            Collection<MavenCoordinate> userdevLibraries,
            ExtensibleClasspath classpath) {
        if (!isForge1122UniversalArtifact(universalArtifact)) {
            return false;
        }

        classpath.addMavenLibraries(normalizeLibraries(userdevLibraries));
        return true;
    }

    private static boolean isForge1122UniversalArtifact(String universalArtifact) {
        if (!universalArtifact.matches("[^:]+:[^:]+:[^:@]+(?::[^@]+)?(?:@[^:]+)?")) {
            return false;
        }
        var universal = MavenCoordinate.parse(universalArtifact);
        return FORGE_GROUP.equals(universal.groupId())
                && FORGE_ARTIFACT.equals(universal.artifactId())
                && universal.version().startsWith("1.12.2-");
    }

    private static Collection<MavenCoordinate> normalizeLibraries(Collection<MavenCoordinate> userdevLibraries) {
        return userdevLibraries.stream()
                .map(LegacyForgeClasspath::normalizeLibrary)
                .filter(Objects::nonNull)
                .toList();
    }

    private static MavenCoordinate normalizeLibrary(MavenCoordinate library) {
        if (library.groupId().equals("org.scala-lang.plugins")
                || library.groupId().equals("org.scala-lang")
                && library.artifactId().equals("scala-actors-migration_2.11")) {
            return null;
        }

        var hasMoved = switch (library.artifactId()) {
            case "scala-parser-combinators_2.11", "scala-swing_2.11" -> library.version().equals("1.0.1");
            case "scala-xml_2.11" -> library.version().equals("1.0.2");
            default -> false;
        };
        if (!library.groupId().equals("org.scala-lang") || !hasMoved) {
            return library;
        }

        return new MavenCoordinate("org.scala-lang.modules", library.artifactId(), library.extension(),
                library.classifier(), library.version());
    }
}
