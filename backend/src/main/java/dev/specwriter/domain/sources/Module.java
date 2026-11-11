package dev.specwriter.domain.sources;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.UUID;

/**
 * Sous-arbre d'une racine reconnu par son descripteur (013, research.md R4) : nom tiré du
 * descripteur, sinon du dossier ; coordonnées {@code groupId:artifactId} pour Maven seulement.
 */
public record Module(UUID rootId, String path, String name, ModuleKind kind, String coordinates) {

    public static final int MAX_NAME = 200;

    public Module {
        path = path == null ? "" : path.replace('\\', '/');
        if (path.startsWith("/") || path.equals("..") || path.startsWith("../") || path.contains("/../")
                || path.endsWith("/..")) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Chemin de module hors de la racine.");
        }
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Module sans nom.");
        }
        if (name.length() > MAX_NAME) {
            name = name.substring(0, MAX_NAME);
        }
        coordinates = kind == ModuleKind.MAVEN ? coordinates : null;
    }

    /** Pseudo-module des fichiers d'une racine hors de tout module, nommé d'après la racine. */
    public static Module root(UUID rootId, String label) {
        return new Module(rootId, "", label, ModuleKind.ROOT, null);
    }

    public ModuleId id() {
        return new ModuleId(rootId, path);
    }
}
