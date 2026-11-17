package fr.kawamind.infrastructure.jira;

import fr.kawamind.infrastructure.platform.HttpTlsConfigurer;
import java.nio.file.Path;
import java.security.KeyStore;

/**
 * Autorité de certification interne d'un Jira on premise (research.md R10) : le PEM indiqué est
 * ajouté à un magasin de confiance dédié au client Jira ({@link HttpTlsConfigurer}, partagé avec
 * Confluence depuis 016). Il n'existe aucun mode « tout accepter ».
 */
public final class JiraTlsConfigurer {

    private JiraTlsConfigurer() {
    }

    public static KeyStore trustStoreFromPem(Path pem) {
        return HttpTlsConfigurer.trustStoreFromPem(pem, "jira");
    }
}
