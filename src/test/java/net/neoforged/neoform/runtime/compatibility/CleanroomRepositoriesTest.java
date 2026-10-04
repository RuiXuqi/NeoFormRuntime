package net.neoforged.neoform.runtime.compatibility;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CleanroomRepositoriesTest {
    @Test
    void detectsCleanroomUserdev() {
        assertThat(CleanroomRepositories.isCleanroomUserdev(List.of(
                "com.cleanroommc:cleanroom:0.6.13-alpha:sources@jar",
                "com.cleanroommc:cleanroom:0.6.13-alpha:universal@jar"))).isTrue();
        assertThat(CleanroomRepositories.isCleanroomUserdev(List.of(
                "net.minecraftforge:forge:1.12.2-14.23.5.2860:sources@jar",
                "net.minecraftforge:forge:1.12.2-14.23.5.2860:universal@jar"))).isFalse();
        assertThat(CleanroomRepositories.isCleanroomUserdev(List.of("net.neoforged:neoforge:21.0.0-beta:userdev"))).isFalse();
        assertThat(CleanroomRepositories.isCleanroomUserdev(List.of("C:\\local\\path\\userdev.jar"))).isFalse();
    }

    @Test
    void cleanroomRepositories() {
        assertThat(CleanroomRepositories.additionalRepositories())
                .containsExactly(
                        CleanroomRepositories.CLEANROOM_RELEASES,
                        CleanroomRepositories.CLEANROOM_SNAPSHOTS,
                        CleanroomRepositories.ARCSEEKERS_RELEASES);
    }
}
