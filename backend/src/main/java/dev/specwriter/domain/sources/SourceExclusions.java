package dev.specwriter.domain.sources;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Exclusions fixes d'une racine de sources (013, research.md R3, FR-004, FR-005), appliquées avant
 * le {@code .gitignore} et les motifs de l'utilisateur : aucun motif ne peut les lever.
 */
public final class SourceExclusions {

    private static final Set<String> GENERATED = Set.of("target", "node_modules", "build", "dist", "out", "bin",
            "coverage", "__pycache__", "vendor");

    private static final Set<String> SENSITIVE_NAMES = Set.of("settings.xml", "settings-security.xml");

    private static final List<String> SENSITIVE_EXTENSIONS = List.of(".pem", ".key", ".p12", ".pfx", ".jks", ".keystore",
            ".truststore", ".kdbx", ".ppk", ".tfvars");

    private static final List<String> SENSITIVE_PREFIXES = List.of("id_rsa", "id_ed25519", "credentials", "kubeconfig");

    private SourceExclusions() {
    }

    /** Dossier caché (nom commençant par un point) ou de fichiers générés : jamais parcouru. */
    public static boolean ignoredDirectory(String name) {
        return name.startsWith(".") || GENERATED.contains(name.toLowerCase(Locale.ROOT));
    }

    /**
     * Fichier qui peut porter un secret : variables d'environnement et autres fichiers cachés,
     * réglages Maven, clés, magasins de certificats. Jamais lu, même si un motif semble l'inclure.
     */
    public static boolean sensitive(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.startsWith(".") || SENSITIVE_NAMES.contains(name) || name.contains("secret")
                || SENSITIVE_EXTENSIONS.stream().anyMatch(name::endsWith)
                || SENSITIVE_PREFIXES.stream().anyMatch(name::startsWith);
    }
}
