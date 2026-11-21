package fr.kawamind.infrastructure.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** T029 : verrou de l'instance, tenu par un autre processus puis rendu à sa mort (019, R1). */
class InstanceLockTest {

    @TempDir
    Path data;

    Process child;

    @AfterEach
    void cleanUp() throws IOException {
        if (child != null) {
            child.destroyForcibly();
        }
        InstanceLock.release();
    }

    Process holdInChild() throws IOException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        child = new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                LockHolder.class.getName(), data.toString()).redirectErrorStream(true).start();
        BufferedReader out = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
        assertThat(out.readLine()).isEqualTo("ACQUIRED");
        return child;
    }

    @Test
    void libreIlEstPris() throws IOException {
        assertThat(InstanceLock.tryAcquire(data)).isEqualTo(InstanceLock.Outcome.ACQUIRED);
        assertThat(InstanceLock.heldDataDir()).contains(data);
        assertThat(Files.exists(data.resolve(InstanceLock.FILE))).isTrue();
    }

    @Test
    void tenuParUnAutreProcessusIlEstOccupe() throws IOException {
        holdInChild();

        assertThat(InstanceLock.tryAcquire(data)).isEqualTo(InstanceLock.Outcome.BUSY);
        assertThat(InstanceLock.held()).isFalse();
    }

    @Test
    void laMortDuProcessusRendLeVerrou() throws Exception {
        holdInChild().destroyForcibly().waitFor(10, TimeUnit.SECONDS);

        assertThat(InstanceLock.tryAcquire(data)).isEqualTo(InstanceLock.Outcome.ACQUIRED);
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void unDossierIllisibleNEstPasUneInstanceEnMarche() throws IOException {
        Path readOnly = Files.createDirectory(data.resolve("lecture-seule"));
        Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("r-x------"));

        assertThatThrownBy(() -> InstanceLock.tryAcquire(readOnly)).isInstanceOf(IOException.class);
    }
}
