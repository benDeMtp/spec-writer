package dev.specwriter.domain.sources;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SourceExclusionsTest {

    @ParameterizedTest
    @ValueSource(strings = {".git", ".idea", ".angular", "target", "node_modules", "build", "dist", "out", "bin", "coverage",
            "__pycache__", "vendor", "Target", "NODE_MODULES"})
    void dossiersGeneresOuCachesIgnores(String name) {
        assertThat(SourceExclusions.ignoredDirectory(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"src", "main", "builder", "targets", "distribution", "binaire", "libs"})
    void dossiersOrdinairesParcourus(String name) {
        assertThat(SourceExclusions.ignoredDirectory(name)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {".env", ".env.local", ".npmrc", "settings.xml", "Settings.XML", "settings-security.xml", "cert.pem",
            "server.key", "store.p12", "store.pfx", "store.jks", "app.keystore", "app.truststore", "coffre.kdbx", "id_rsa",
            "id_rsa.pub", "id_ed25519", "putty.ppk", "credentials", "credentials.json", "db-secret.yaml", "secretariat.md",
            "prod.tfvars", "kubeconfig", "kubeconfig-dev"})
    void fichiersSensiblesJamaisLus(String name) {
        assertThat(SourceExclusions.sensitive(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"keys.java", "pom.xml", "application.properties", "Key.java", "values.yaml", "settings.gradle",
            "README.md"})
    void fichiersOrdinairesLus(String name) {
        assertThat(SourceExclusions.sensitive(name)).isFalse();
    }
}
