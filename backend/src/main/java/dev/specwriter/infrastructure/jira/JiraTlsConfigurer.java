package dev.specwriter.infrastructure.jira;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;

/**
 * Autorité de certification interne d'un Jira on premise (research.md R10) : le PEM indiqué est
 * ajouté à un magasin de confiance dédié au client Jira. Il n'existe aucun mode « tout accepter ».
 */
public final class JiraTlsConfigurer {

    private JiraTlsConfigurer() {
    }

    public static KeyStore trustStoreFromPem(Path pem) {
        try (InputStream in = Files.newInputStream(pem)) {
            Collection<? extends Certificate> certs = CertificateFactory.getInstance("X.509").generateCertificates(in);
            if (certs.isEmpty()) {
                throw new IllegalArgumentException("Aucun certificat dans " + pem.getFileName());
            }
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(null, null);
            int i = 0;
            for (Certificate c : certs) {
                ks.setCertificateEntry("jira-ca-" + i++, c);
            }
            return ks;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Certificat illisible : " + pem.getFileName(), e);
        }
    }
}
