package dev.specwriter.application.knowledge;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.DocSourceRepository;
import dev.specwriter.domain.port.DocumentFiles;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.project.DocSource;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Fichier d'un dossier de documentation du projet, pour ouvrir la page d'un ADR depuis la page des
 * ADR (feature 004, research.md R3). Seules les sources de type dossier du projet sont lues, et
 * seulement des pages, feuilles de style, images et polices : jamais de script.
 */
public class ReadDocFile {

    /** Fichier servi et son type. */
    public record Served(String mediaType, byte[] content) {
    }

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"), Map.entry("htm", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"), Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"),
            Map.entry("svg", "image/svg+xml"), Map.entry("webp", "image/webp"), Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"));

    private final ProjectRepository projects;
    private final DocSourceRepository docSources;
    private final DocumentFiles files;

    public ReadDocFile(ProjectRepository projects, DocSourceRepository docSources, DocumentFiles files) {
        this.projects = projects;
        this.docSources = docSources;
        this.files = files;
    }

    public Optional<Served> run(UUID projectId, String path) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
        String type = path == null ? null : TYPES.get(extension(path));
        if (type == null) {
            return Optional.empty();
        }
        return docSources.list(projectId).stream().filter(s -> s.kind() == DocSource.Kind.FOLDER)
                .map(s -> files.read(s, path)).flatMap(Optional::stream).findFirst()
                .map(f -> new Served(type, f.content()));
    }

    private static String extension(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 || dot < path.lastIndexOf('/') ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
