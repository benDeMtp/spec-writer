package fr.kawamind.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.error.DomainException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** T019 (016) : serveur Confluence, mêmes règles que Jira. */
class ConfluenceSettingsTest {

    @TempDir
    Path dir;

    @Test
    void adresseNormalisee() {
        ConfluenceSettings s = ConfluenceSettings.create(" https://wiki.example.com/confluence/ ", null, false,
                SecretPersistence.KEYCHAIN);
        assertThat(s.serverUrl()).isEqualTo("https://wiki.example.com/confluence");
        assertThat(s.insecureHttp()).isFalse();
        assertThatThrownBy(() -> ConfluenceSettings.create("wiki", null, false, SecretPersistence.KEYCHAIN))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.userMessage()).contains("adresse du serveur Confluence"));
        assertThatThrownBy(() -> ConfluenceSettings.create("https://" + "x".repeat(2_000), null, false,
                SecretPersistence.KEYCHAIN)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ConfluenceSettings.create("https://ada:secret@wiki", null, false,
                SecretPersistence.KEYCHAIN)).isInstanceOf(DomainException.class);
    }

    @Test
    void httpSeulementApresConfirmation() {
        assertThatThrownBy(() -> ConfluenceSettings.create("http://wiki.local", null, false, SecretPersistence.KEYCHAIN))
                .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.userMessage()).contains("n'est pas chiffrée (http)"));
        assertThat(ConfluenceSettings.create("http://wiki.local", null, true, SecretPersistence.KEYCHAIN).insecureHttp())
                .isTrue();
    }

    @Test
    void certificatLisibleOuRefusExplicite() throws Exception {
        Path pem = Files.writeString(dir.resolve("ca.pem"), "-----BEGIN CERTIFICATE-----");
        assertThat(ConfluenceSettings.create("https://wiki", pem.toString(), false, SecretPersistence.KEYCHAIN)
                .caCertificatePath()).isEqualTo(pem.toString());
        assertThatThrownBy(() -> ConfluenceSettings.create("https://wiki", dir.resolve("absent.pem").toString(), false,
                SecretPersistence.KEYCHAIN)).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.userMessage()).contains("PEM"));
        assertThatThrownBy(() -> ConfluenceSettings.create("https://wiki", "relatif.pem", false,
                SecretPersistence.KEYCHAIN)).isInstanceOf(DomainException.class);
    }

    @Test
    void resultatDuTest() {
        ConfluenceSettings s = ConfluenceSettings.create("https://wiki", null, false, SecretPersistence.KEYCHAIN)
                .withTest(java.time.Instant.EPOCH, ConfluenceTestResult.OK, "8.5.4")
                .withPersistence(SecretPersistence.MEMORY_ONLY);
        assertThat(s.serverVersion()).isEqualTo("8.5.4");
        assertThat(s.lastTest().result()).isEqualTo(ConfluenceTestResult.OK);
        assertThat(s.secretPersistence()).isEqualTo(SecretPersistence.MEMORY_ONLY);
        assertThat(ConfluenceTestResult.UNAUTHORIZED.userMessage()).contains("jeton");
    }
}
