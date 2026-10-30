package dev.specwriter.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LaunchTokenServiceTest {

    @TempDir
    Path dir;

    @Test
    void jetonAUsageUniqueEtSessionsEnMemoire() {
        LaunchTokenService tokens = new LaunchTokenService((Path) null);
        String cookie = tokens.exchange(tokens.launchToken()).orElseThrow();
        assertThat(tokens.isValidSession(cookie)).isTrue();
        assertThat(tokens.exchange(tokens.launchToken())).isEmpty();
        assertThat(tokens.exchange("faux")).isEmpty();
        assertThat(tokens.isValidSession(null)).isFalse();
        // sans fichier, un redémarrage invalide les sessions
        assertThat(new LaunchTokenService((Path) null).isValidSession(cookie)).isFalse();
    }

    @Test
    void enDevModeLesSessionsSurviventAuRechargement() throws IOException {
        Path file = dir.resolve("dev-sessions");
        LaunchTokenService first = new LaunchTokenService(file);
        String cookie = first.exchange(first.launchToken()).orElseThrow();

        LaunchTokenService reloaded = new LaunchTokenService(file);
        assertThat(reloaded.isValidSession(cookie)).isTrue();
        assertThat(reloaded.launchToken()).isNotEqualTo(first.launchToken());
        assertThat(Files.readString(file)).doesNotContain(first.launchToken());
        if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(file))).isEqualTo("rw-------");
        }
    }

    @Test
    void fichierIllisibleIgnore() throws IOException {
        Path file = dir.resolve("absent/dev-sessions");
        assertThat(new LaunchTokenService(file).isValidSession("x")).isFalse();
        Path blank = Files.writeString(dir.resolve("vide"), "\n\n");
        assertThat(new LaunchTokenService(blank).isValidSession("")).isFalse();
    }
}
