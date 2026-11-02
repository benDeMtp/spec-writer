package dev.specwriter.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import org.junit.jupiter.api.Test;

class JiraSettingsTest {

    private static JiraSettings create(String url, String key, boolean insecureOk) {
        return JiraSettings.create(url, key, null, insecureOk, SecretPersistence.KEYCHAIN);
    }

    @Test
    void adresseNormalisee() {
        assertThat(create("https://Jira.Example.com/jira/", "PROJ", false).serverUrl())
                .isEqualTo("https://Jira.Example.com/jira");
    }

    @Test
    void adresseAvecRequeteOuFragmentRefusee() {
        for (String url : new String[] {"https://jira.example.com/?a=1", "https://jira.example.com/#x",
                "ftp://jira.example.com", "jira.example.com", "", "https://user:pwd@jira.example.com"}) {
            assertThatThrownBy(() -> create(url, "PROJ", false)).as(url)
                    .isInstanceOf(DomainException.class)
                    .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.VALIDATION_FAILED);
        }
    }

    @Test
    void httpRefuseSansConfirmation() {
        assertThatThrownBy(() -> create("http://jira.local", "PROJ", false))
                .isInstanceOf(DomainException.class)
                .hasFieldOrPropertyWithValue("userMessage",
                        "Cette adresse n'est pas chiffrée (http). Votre jeton circulerait en clair : utilisez https, "
                                + "ou confirmez l'avertissement.");
        JiraSettings ok = create("http://jira.local", "PROJ", true);
        assertThat(ok.insecureHttp()).isTrue();
    }

    @Test
    void cleDeProjet() {
        assertThat(create("https://j", "SPEC_2", false).defaultProjectKey()).isEqualTo("SPEC_2");
        for (String key : new String[] {"proj", "P", "1ABC", "AB-C", null}) {
            assertThatThrownBy(() -> create("https://j", key, false)).as(String.valueOf(key))
                    .isInstanceOf(DomainException.class);
        }
    }

    @Test
    void versionsSupportees() {
        assertThat(JiraSettings.isSupportedVersion("8.14.0")).isTrue();
        assertThat(JiraSettings.isSupportedVersion("9.12.3")).isTrue();
        assertThat(JiraSettings.isSupportedVersion("11.3.1")).isTrue();
        assertThat(JiraSettings.isSupportedVersion("8.13.9")).isFalse();
        assertThat(JiraSettings.isSupportedVersion("7.0")).isFalse();
        assertThat(JiraSettings.isSupportedVersion("inconnue")).isFalse();
        assertThat(JiraSettings.isSupportedVersion(null)).isFalse();
    }

    @Test
    void certificatFacultatif() {
        assertThat(JiraSettings.create("https://j", "PROJ", "  ", false, SecretPersistence.KEYCHAIN).caCertificatePath()).isNull();
        assertThat(JiraSettings.create("https://j", "PROJ", "/etc/ca.pem", false, SecretPersistence.KEYCHAIN).caCertificatePath())
                .isEqualTo("/etc/ca.pem");
    }
}
