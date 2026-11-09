package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.port.DocumentFolder;
import dev.specwriter.domain.port.DocumentationReader.Page;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.domain.project.ExclusionPatterns;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Dossier de documents sur le poste (006, research.md R1 à R4, R7) : parcours récursif sans suivre
 * les liens symboliques ni ouvrir de fichier, puis lecture d'un document à la demande, sur un fil
 * virtuel et avec un délai : un fichier à la demande non téléchargé, hors ligne, est signalé
 * indisponible au lieu de bloquer l'indexation. Aucun fichier n'est écrit.
 */
public final class LocalDocumentFolder implements DocumentFolder {

    static final Set<String> FORMATS = Set.of("docx", "xlsx", "pdf", "html", "htm");

    private final Limits limits;

    public LocalDocumentFolder(Limits limits) {
        this.limits = limits;
    }

    @Override
    public Scan scan(DocSource source) {
        Path root = Path.of(source.location());
        if (!Files.exists(root)) {
            return problem(UnreadReason.FOLDER_NOT_FOUND);
        }
        if (!Files.isDirectory(root)) {
            return problem(UnreadReason.NOT_A_FOLDER);
        }
        if (!Files.isReadable(root)) {
            return problem(UnreadReason.ACCESS_DENIED);
        }
        Walk walk = new Walk(root, source.exclusions(), System.nanoTime() + limits.maxDuration().toNanos());
        try {
            Files.walkFileTree(root, walk);
        } catch (IOException e) {
            return problem(UnreadReason.UNREADABLE);
        }
        walk.retained.sort(Comparator.comparing(FileEntry::path));
        walk.excluded.sort(Comparator.naturalOrder());
        walk.ignored.sort(Comparator.naturalOrder());
        return new Scan(List.copyOf(walk.retained), List.copyOf(walk.excluded), List.copyOf(walk.ignored),
                walk.stopped, null);
    }

    @Override
    public Reading read(DocSource source, String path) {
        Path root = Path.of(source.location()).toAbsolutePath().normalize();
        Path file = root.resolve(path).normalize();
        if (!file.startsWith(root) || Files.isSymbolicLink(file)) {
            return Reading.failed(UnreadReason.UNREADABLE);
        }
        String format = extension(fileName(file));
        if (!FORMATS.contains(format)) {
            return Reading.failed(UnreadReason.UNREADABLE);
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile()) {
                return Reading.failed(UnreadReason.UNREADABLE);
            }
            if (attributes.size() > limits.maxFileBytes()) {
                return Reading.failed(UnreadReason.TOO_LARGE);
            }
        } catch (NoSuchFileException e) {
            return Reading.failed(UnreadReason.UNAVAILABLE);
        } catch (IOException e) {
            return Reading.failed(UnreadReason.UNREADABLE);
        }
        DocumentExtraction.Result result = DocumentExtraction.extract(file, format, limits);
        if (result.failure() != null) {
            return Reading.failed(result.failure());
        }
        Extracted doc = result.document();
        String ref = DocumentFolder.ref(source, path);
        return Reading.of(new Page(ref, doc.title(), doc.text(), Sections.ofText(ref, doc.title(), doc.text())));
    }

    /** Nom du fichier, vide pour une racine sans nom. */
    static String fileName(Path file) {
        Path name = file.getFileName();
        return name == null ? "" : name.toString();
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static Scan problem(UnreadReason reason) {
        return new Scan(List.of(), List.of(), List.of(), false, reason);
    }

    /** Parcours : documents retenus, exclus, ignorés ; arrêté aux limites de la source. */
    private final class Walk extends SimpleFileVisitor<Path> {
        private final Path root;
        private final ExclusionPatterns exclusions;
        private final long deadline;
        final List<FileEntry> retained = new ArrayList<>();
        final List<String> excluded = new ArrayList<>();
        final List<String> ignored = new ArrayList<>();
        boolean stopped;

        Walk(Path root, ExclusionPatterns exclusions, long deadline) {
            this.root = root;
            this.exclusions = exclusions;
            this.deadline = deadline;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            if (!dir.equals(root) && ExclusionPatterns.alwaysIgnored(relative(dir))) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (!attrs.isRegularFile()) {
                // lien symbolique ou fichier spécial : jamais suivi
                return FileVisitResult.CONTINUE;
            }
            String path = relative(file);
            if (ExclusionPatterns.alwaysIgnored(path)) {
                return FileVisitResult.CONTINUE;
            }
            if (exclusions.excludes(path)) {
                excluded.add(path);
                return FileVisitResult.CONTINUE;
            }
            if (!FORMATS.contains(extension(fileName(file)))) {
                ignored.add(path);
                return FileVisitResult.CONTINUE;
            }
            if (retained.size() >= limits.maxDocuments() || System.nanoTime() > deadline) {
                stopped = true;
                return FileVisitResult.TERMINATE;
            }
            retained.add(new FileEntry(path, attrs.size(), Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis())));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException e) {
            // fichier ou sous-dossier illisible : ignoré, le reste est parcouru
            if (!(e instanceof AccessDeniedException)) {
                ignored.add(relative(file));
            }
            return FileVisitResult.CONTINUE;
        }

        private String relative(Path path) {
            return root.relativize(path).toString().replace('\\', '/');
        }
    }
}
