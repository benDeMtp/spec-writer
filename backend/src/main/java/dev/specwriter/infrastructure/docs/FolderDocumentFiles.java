package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.port.DocumentFiles;
import dev.specwriter.domain.project.DocSource;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;
import org.jboss.logging.Logger;

/**
 * Fichiers d'un dossier de documentation déclaré (feature 004, research.md R3). Le chemin demandé
 * est résolu dans le dossier puis comparé à son chemin réel : {@code ..}, chemin absolu et lien
 * symbolique qui sortent du dossier ne donnent rien. Ni le chemin ni le contenu ne sont journalisés.
 */
@ApplicationScoped
public class FolderDocumentFiles implements DocumentFiles {

    /** Au-delà, le fichier n'est pas servi (une page ou une image de documentation reste bien en dessous). */
    static final long MAX_SIZE = 10L * 1024 * 1024;

    private static final Logger LOG = Logger.getLogger(FolderDocumentFiles.class);

    @Override
    public boolean exists(DocSource folder, String path) {
        return resolve(folder, path).isPresent();
    }

    @Override
    public Optional<DocumentFile> read(DocSource folder, String path) {
        return resolve(folder, path).flatMap(file -> {
            try {
                if (Files.size(file) > MAX_SIZE) {
                    LOG.info("Documentation : fichier trop volumineux non servi");
                    return Optional.empty();
                }
                return Optional.of(new DocumentFile(path, Files.readAllBytes(file)));
            } catch (IOException e) {
                LOG.infof("Documentation : fichier illisible (%s)", e.getClass().getSimpleName());
                return Optional.empty();
            }
        });
    }

    private static Optional<Path> resolve(DocSource folder, String path) {
        if (folder.kind() != DocSource.Kind.FOLDER || path == null || path.isBlank()) {
            return Optional.empty();
        }
        try {
            Path relative = Path.of(path);
            if (relative.isAbsolute() || relative.getRoot() != null) {
                return refused();
            }
            Path root = Path.of(folder.location()).toRealPath();
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                return file.startsWith(root) ? Optional.empty() : refused();
            }
            Path real = file.toRealPath();
            return real.startsWith(root) ? Optional.of(real) : refused();
        } catch (InvalidPathException | IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<Path> refused() {
        LOG.info("Documentation : chemin hors du dossier déclaré refusé");
        return Optional.empty();
    }
}
