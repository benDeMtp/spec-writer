package dev.specwriter.infrastructure.jira;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Autorité de test et certificat serveur 127.0.0.1 signé par elle, générés avec keytool. */
final class TestCertificates {

    static final String PASSWORD = "changeit";

    final Path caPem;
    final Path serverKeystore;
    final Path otherCaPem;

    private TestCertificates(Path caPem, Path serverKeystore, Path otherCaPem) {
        this.caPem = caPem;
        this.serverKeystore = serverKeystore;
        this.otherCaPem = otherCaPem;
    }

    static TestCertificates generate(Path dir) throws Exception {
        Files.createDirectories(dir);
        Path ca = dir.resolve("ca.p12");
        Path caPem = dir.resolve("ca.pem");
        Path server = dir.resolve("server.p12");
        Path csr = dir.resolve("server.csr");
        Path serverPem = dir.resolve("server.pem");
        Path other = dir.resolve("other.p12");
        Path otherPem = dir.resolve("other.pem");
        for (Path p : List.of(ca, caPem, server, csr, serverPem, other, otherPem)) {
            Files.deleteIfExists(p);
        }
        keytool("-genkeypair", "-alias", "ca", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=Test CA",
                "-ext", "bc:c", "-validity", "2", "-keystore", ca.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD);
        keytool("-exportcert", "-rfc", "-alias", "ca", "-keystore", ca.toString(), "-storepass", PASSWORD, "-file", caPem.toString());
        keytool("-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=127.0.0.1",
                "-validity", "2", "-keystore", server.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD);
        keytool("-certreq", "-alias", "server", "-keystore", server.toString(), "-storepass", PASSWORD, "-file", csr.toString());
        keytool("-gencert", "-alias", "ca", "-keystore", ca.toString(), "-storepass", PASSWORD, "-infile", csr.toString(),
                "-outfile", serverPem.toString(), "-rfc", "-ext", "SAN=IP:127.0.0.1", "-validity", "2");
        keytool("-importcert", "-alias", "ca", "-keystore", server.toString(), "-storepass", PASSWORD, "-file", caPem.toString(), "-noprompt");
        keytool("-importcert", "-alias", "server", "-keystore", server.toString(), "-storepass", PASSWORD, "-file", serverPem.toString());
        keytool("-genkeypair", "-alias", "other", "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=Autre CA",
                "-ext", "bc:c", "-validity", "2", "-keystore", other.toString(), "-storetype", "PKCS12", "-storepass", PASSWORD);
        keytool("-exportcert", "-rfc", "-alias", "other", "-keystore", other.toString(), "-storepass", PASSWORD, "-file", otherPem.toString());
        return new TestCertificates(caPem, server, otherPem);
    }

    private static void keytool(String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        if (p.waitFor() != 0) {
            throw new IllegalStateException("keytool a échoué : " + out);
        }
    }
}
