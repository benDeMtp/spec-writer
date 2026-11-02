package dev.specwriter.spike;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.port.SecretStore;
import dev.specwriter.infrastructure.secrets.KeychainSecretStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Vérifie le vrai trousseau du poste (exclu du build : tag spike). */
@Tag("spike")
class OsKeychainSpikeIT {

    @Test
    void allerRetourDansLeTrousseauDuSysteme() {
        KeychainSecretStore store = KeychainSecretStore.system();
        store.put("spike.test", "TEST-JIRA-PAT");
        assertThat(store.persistence()).isEqualTo(SecretStore.Persistence.KEYCHAIN);
        assertThat(store.get("spike.test")).contains("TEST-JIRA-PAT");
        store.delete("spike.test");
        assertThat(store.get("spike.test")).isEmpty();
    }
}
