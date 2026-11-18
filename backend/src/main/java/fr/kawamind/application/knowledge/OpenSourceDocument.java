package fr.kawamind.application.knowledge;

import fr.kawamind.application.settings.ManageBrowser;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.port.BrowserPort;
import fr.kawamind.domain.port.DocSourceRepository;
import fr.kawamind.domain.port.LocalFiles;
import fr.kawamind.domain.project.DocSource;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Ouvre sur le poste le document d'une source citée qui n'a pas d'adresse web : page d'un site
 * Antora généré déclaré comme dossier (dans le navigateur, à sa section), document d'un dossier
 * OneDrive (dans son application). Seuls les fichiers des dossiers déclarés par le projet
 * s'ouvrent ; un navigateur refuse un lien {@code file:} depuis la page de l'outil.
 */
public class OpenSourceDocument {

    /** Où le document s'est ouvert. */
    public enum Opened {
        BROWSER, APPLICATION
    }

    static final String DOCUMENTS_PREFIX = "docs:";
    static final int MAX_REF = 4_000;

    private final DocSourceRepository sources;
    private final LocalFiles files;
    private final ManageBrowser browser;
    private final BrowserPort system;

    public OpenSourceDocument(DocSourceRepository sources, LocalFiles files, ManageBrowser browser,
            BrowserPort system) {
        this.sources = sources;
        this.files = files;
        this.browser = browser;
        this.system = system;
    }

    /** Référence d'une source sans adresse web qui peut désigner un fichier d'un dossier déclaré. */
    public static boolean local(String ref) {
        return ref != null && !ref.isBlank() && !ref.toLowerCase(Locale.ROOT).startsWith("http")
                && !ref.contains("://");
    }

    public Opened run(UUID projectId, String ref) {
        if (!local(ref) || ref.length() > MAX_REF) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Cette source n'est pas un fichier du poste.");
        }
        Target target = target(projectId, ref);
        URI uri = target.roots().stream().map(root -> files.locate(root, target.path()))
                .flatMap(Optional::stream).findFirst()
                .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND,
                        "Ce document n'est plus dans les dossiers déclarés du projet. Réindexez ses sources."));
        boolean page = target.path().toLowerCase(Locale.ROOT).matches(".*\\.html?$");
        boolean opened;
        Opened where;
        if (page) {
            String address = target.fragment() == null ? uri.toString() : uri + "#" + target.fragment();
            opened = browser.open(address).opened();
            where = Opened.BROWSER;
        } else {
            opened = system.open(null, uri.toString());
            where = Opened.APPLICATION;
        }
        if (!opened) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Le système n'a pas pu ouvrir ce document.");
        }
        return where;
    }

    /** Dossiers où chercher, chemin relatif et section de la page. */
    private record Target(List<String> roots, String path, String fragment) {
    }

    private Target target(UUID projectId, String ref) {
        if (ref.startsWith(DOCUMENTS_PREFIX)) {
            // docs:<source>/<chemin>#<n° de section> : le numéro n'est pas une ancre du document
            String body = ref.substring(DOCUMENTS_PREFIX.length());
            int slash = body.indexOf('/');
            UUID sourceId = slash > 0 ? uuid(body.substring(0, slash)) : null;
            String path = slash > 0 ? body.substring(slash + 1).replaceFirst("#\\d+$", "") : "";
            List<String> roots = sourceId == null ? List.of() : sources.find(sourceId)
                    .filter(s -> s.projectId().equals(projectId) && s.kind() == DocSource.Kind.DOCUMENTS)
                    .map(DocSource::location).stream().toList();
            return new Target(roots, path, null);
        }
        int hash = ref.indexOf('#');
        String path = hash >= 0 ? ref.substring(0, hash) : ref;
        String fragment = hash >= 0 && hash + 1 < ref.length() ? ref.substring(hash + 1) : null;
        List<String> roots = sources.list(projectId).stream().filter(s -> s.kind() == DocSource.Kind.FOLDER)
                .map(DocSource::location).toList();
        return new Target(roots, path, fragment);
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
