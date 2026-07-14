package net.neoforged.neoform.runtime.actions;

import net.neoforged.neoform.runtime.artifacts.ClasspathItem;
import net.neoforged.neoform.runtime.cache.CacheKeyBuilder;
import net.neoforged.neoform.runtime.graph.NodeOutput;
import net.neoforged.neoform.runtime.manifests.MinecraftLibrary;
import net.neoforged.neoform.runtime.manifests.MinecraftVersionManifest;
import net.neoforged.neoform.runtime.utils.MavenCoordinate;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Utility class for building a classpath.
 */
public class ExtensibleClasspath {
    private List<ClasspathItem> additionalClasspath = new ArrayList<>();
    private final Set<String> excludedMinecraftLibraryGroups = new LinkedHashSet<>();
    private final Set<String> excludedMinecraftLibraryModules = new LinkedHashSet<>();
    @Nullable
    private List<ClasspathItem> overriddenClasspath;

    public List<ClasspathItem> getEffectiveClasspath() {
        if (overriddenClasspath != null) {
            return overriddenClasspath;
        } else {
            return List.copyOf(additionalClasspath);
        }
    }

    public void addMinecraftLibraries(Collection<MinecraftLibrary> libraries) {
        for (var library : libraries) {
            var coordinate = library.getMavenCoordinate();
            if (library.rulesMatch() && library.getArtifactDownload() != null
                    && !excludedMinecraftLibraryGroups.contains(coordinate.groupId())
                    && !excludedMinecraftLibraryModules.contains(coordinate.groupId() + ":" + coordinate.artifactId())) {
                add(ClasspathItem.of(library));
            }
        }
    }

    public void excludeMinecraftLibraryGroup(String groupId) {
        excludedMinecraftLibraryGroups.add(Objects.requireNonNull(groupId, "groupId"));
    }

    public void excludeMinecraftLibrary(String groupId, String artifactId) {
        excludedMinecraftLibraryModules.add(Objects.requireNonNull(groupId, "groupId") + ":"
                + Objects.requireNonNull(artifactId, "artifactId"));
    }

    public void addMavenLibraries(Collection<MavenCoordinate> additionalLibraries) {
        for (var library : additionalLibraries) {
            add(ClasspathItem.of(library));
        }
    }

    public void addPaths(Collection<Path> additionalPaths) {
        for (var path : additionalPaths) {
            add(ClasspathItem.of(path));
        }
    }

    public void addAll(Iterable<ClasspathItem> classpathItems) {
        for (var classpathItem : classpathItems) {
            add(classpathItem);
        }
    }

    public void add(ClasspathItem classpathItem) {
        // Ensure that previously added libraries of the same group:artifact:classifier are overridden to avoid
        // the same library from being present on the classpath twice.
        var coordinate = getMavenCoordinate(classpathItem);
        if (coordinate != null) {
            this.additionalClasspath.removeIf(existingItem -> {
                var existingCoord = getMavenCoordinate(existingItem);
                return existingCoord != null && existingCoord.equalsWithoutVersion(coordinate);
            });
        }

        this.additionalClasspath.add(classpathItem);
    }

    @Nullable
    private MavenCoordinate getMavenCoordinate(ClasspathItem classpathItem) {
        return switch (classpathItem) {
            case ClasspathItem.MavenCoordinateItem mavenCoordinateItem -> mavenCoordinateItem.coordinate();
            case ClasspathItem.MinecraftLibraryItem minecraftLibraryItem -> minecraftLibraryItem.library().getMavenCoordinate();
            default -> null;
        };
    }

    public List<ClasspathItem> getAdditionalClasspath() {
        return additionalClasspath;
    }

    public void setAdditionalClasspath(List<ClasspathItem> additionalClasspath) {
        Objects.requireNonNull(additionalClasspath, "additionalClasspath");
        this.additionalClasspath.clear();
        additionalClasspath.forEach(this::add);
    }

    public @Nullable List<ClasspathItem> getOverriddenClasspath() {
        return overriddenClasspath;
    }

    public void setOverriddenClasspath(@Nullable List<ClasspathItem> overriddenClasspath) {
        if (overriddenClasspath != null) {
            this.overriddenClasspath = List.copyOf(overriddenClasspath);
        } else {
            this.overriddenClasspath = null;
        }
    }

    public boolean isEmpty() {
        return getEffectiveClasspath().isEmpty();
    }

    public void computeCacheKey(String prefix, CacheKeyBuilder ck) {
        ck.addStrings(prefix + " excluded Minecraft library groups",
                excludedMinecraftLibraryGroups.stream().sorted().toList());
        ck.addStrings(prefix + " excluded Minecraft library modules",
                excludedMinecraftLibraryModules.stream().sorted().toList());

        List<ClasspathItem> effectiveItems;
        if (overriddenClasspath != null) {
            effectiveItems = overriddenClasspath;
            prefix = "overridden " + prefix;
        } else {
            effectiveItems = additionalClasspath;
            prefix = "additional " + prefix;
        }

        for (int i = 0; i < effectiveItems.size(); i++) {
            var component = String.format(Locale.ROOT, "%s[%03d]", prefix, i);
            var item = effectiveItems.get(i);

            switch (item) {
                case ClasspathItem.MavenCoordinateItem(MavenCoordinate coordinate, URI uri) -> {
                    if (uri != null) {
                        ck.add(component, coordinate + " from " + uri);
                    } else {
                        ck.add(component, coordinate.toString());
                    }
                }
                case ClasspathItem.MinecraftLibraryItem(MinecraftLibrary library) -> {
                    var artifactDownload = library.getArtifactDownload();
                    if (artifactDownload != null) {
                        ck.add(component, library.artifactId() + " [" + artifactDownload.checksum() + "]");
                    } else {
                        ck.add(component, library.artifactId());
                    }
                }
                case ClasspathItem.PathItem(Path path) -> ck.addPath(component, path);
                case ClasspathItem.NodeOutputItem(NodeOutput output) -> ck.addPath(component, output.getResultPath());
            }
        }
    }

    /**
     * Merges the libraries from a Minecraft version manifest with the entries in this classpath.
     * Entries in the manifest are considered to be of lower priority than the entries already present
     * in the classpath.
     */
    public ExtensibleClasspath mergeWithMinecraftLibraries(MinecraftVersionManifest versionManifest) {
        if (overriddenClasspath == null) {
            var mergedClasspath = new ExtensibleClasspath();
            mergedClasspath.excludedMinecraftLibraryGroups.addAll(excludedMinecraftLibraryGroups);
            mergedClasspath.excludedMinecraftLibraryModules.addAll(excludedMinecraftLibraryModules);
            mergedClasspath.addMinecraftLibraries(versionManifest.libraries());
            mergedClasspath.addAll(getEffectiveClasspath());
            return mergedClasspath;
        } else {
            return this;
        }
    }

    public ExtensibleClasspath copy() {
        var result = new ExtensibleClasspath();
        result.overriddenClasspath = overriddenClasspath;
        result.additionalClasspath = new ArrayList<>(additionalClasspath);
        result.excludedMinecraftLibraryGroups.addAll(excludedMinecraftLibraryGroups);
        result.excludedMinecraftLibraryModules.addAll(excludedMinecraftLibraryModules);
        return result;
    }
}
