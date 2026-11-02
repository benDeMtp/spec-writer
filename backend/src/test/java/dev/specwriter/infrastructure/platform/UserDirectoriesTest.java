package dev.specwriter.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.io.TempDir;

class UserDirectoriesTest {

    private static UserDirectories dirs(String os, String home, Map<String, String> env) {
        return new UserDirectories(env, Map.of("os.name", os, "user.home", home));
    }

    @Test
    void macos() {
        UserDirectories d = dirs("Mac OS X", "/Users/ada", Map.of());
        assertThat(d.dataDir()).isEqualTo(Path.of("/Users/ada", "Library", "Application Support", "spec-writer"));
        assertThat(d.configDir()).isEqualTo(Path.of("/Users/ada", "Library", "Application Support", "spec-writer", "config"));
    }

    @Test
    void windowsUtiliseLocalAppDataEtPasLeProfilItinerant() {
        UserDirectories d = dirs("Windows 11", "C:\\Users\\ada",
                Map.of("LOCALAPPDATA", "C:\\Users\\ada\\AppData\\Local", "APPDATA", "C:\\Users\\ada\\AppData\\Roaming"));
        assertThat(d.dataDir()).isEqualTo(Path.of("C:\\Users\\ada\\AppData\\Local", "spec-writer", "data"));
        assertThat(d.configDir()).isEqualTo(Path.of("C:\\Users\\ada\\AppData\\Local", "spec-writer", "config"));
    }

    @Test
    void windowsSansLocalAppData() {
        UserDirectories d = dirs("Windows 10", "C:\\Users\\ada", Map.of());
        assertThat(d.dataDir()).isEqualTo(Path.of("C:\\Users\\ada", "AppData", "Local", "spec-writer", "data"));
    }

    @Test
    void linuxAvecXdg() {
        UserDirectories d = dirs("Linux", "/home/ada", Map.of("XDG_DATA_HOME", "/data/ada", "XDG_CONFIG_HOME", "/cfg/ada"));
        assertThat(d.dataDir()).isEqualTo(Path.of("/data/ada", "spec-writer"));
        assertThat(d.configDir()).isEqualTo(Path.of("/cfg/ada", "spec-writer"));
    }

    @Test
    void linuxParDefaut() {
        UserDirectories d = dirs("Linux", "/home/ada", Map.of());
        assertThat(d.dataDir()).isEqualTo(Path.of("/home/ada", ".local", "share", "spec-writer"));
        assertThat(d.configDir()).isEqualTo(Path.of("/home/ada", ".config", "spec-writer"));
    }

    @Test
    void xdgRelatifIgnore() {
        UserDirectories d = dirs("Linux", "/home/ada", Map.of("XDG_DATA_HOME", "relatif"));
        assertThat(d.dataDir()).isEqualTo(Path.of("/home/ada", ".local", "share", "spec-writer"));
    }

    @Test
    void proprieteDataDirPrioritaire() {
        UserDirectories d = new UserDirectories(Map.of(),
                Map.of("os.name", "Linux", "user.home", "/home/ada", "specwriter.data-dir", "/tmp/sw"));
        assertThat(d.dataDir()).isEqualTo(Path.of("/tmp/sw"));
        assertThat(d.configDir()).isEqualTo(Path.of("/tmp/sw", "config"));
    }

    @Test
    @DisabledOnOs(WINDOWS)
    void creationEnRwxSeulementPourLeProprietaire(@TempDir Path tmp) throws Exception {
        Path cible = tmp.resolve("a").resolve("spec-writer");
        UserDirectories.createPrivateDirectories(cible, true);
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(cible);
        assertThat(PosixFilePermissions.toString(perms)).isEqualTo("rwx------");
    }

    @Test
    void creationSansAttributPosix(@TempDir Path tmp) throws Exception {
        Path cible = tmp.resolve("b");
        UserDirectories.createPrivateDirectories(cible, false);
        assertThat(cible).isDirectory();
    }
}
