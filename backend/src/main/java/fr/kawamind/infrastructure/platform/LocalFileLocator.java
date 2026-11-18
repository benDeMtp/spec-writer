package fr.kawamind.infrastructure.platform;

import fr.kawamind.domain.port.LocalFiles;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Fichier d'un dossier déclaré (site Antora généré, dossier OneDrive) : chemin relatif résolu sous
 * la racine réelle, sans lien symbolique ni sortie du dossier, fichier ordinaire seulement.
 */
public final class LocalFileLocator implements LocalFiles {

    @Override
    public Optional<URI> locate(String root, String relative) {
        if (root == null || relative == null || relative.isBlank()) {
            return Optional.empty();
        }
        try {
            Path base = Path.of(root).toRealPath();
            Path rel = Path.of(relative);
            if (rel.isAbsolute()) {
                return Optional.empty();
            }
            Path file = base.resolve(rel).normalize();
            if (!file.startsWith(base) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    || !file.toRealPath().equals(file)) {
                return Optional.empty();
            }
            return Optional.of(file.toUri());
        } catch (IOException | InvalidPathException | SecurityException e) {
            return Optional.empty();
        }
    }
}
