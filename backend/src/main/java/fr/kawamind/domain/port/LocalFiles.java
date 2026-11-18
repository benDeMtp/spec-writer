package fr.kawamind.domain.port;

import java.net.URI;
import java.util.Optional;

/** Fichiers des dossiers déclarés par un projet, lus sur le poste sans en sortir. */
public interface LocalFiles {

    /**
     * Adresse {@code file:} du fichier {@code relative} sous le dossier {@code root} : vide s'il n'existe
     * pas, n'est pas un fichier ordinaire, passe par un lien symbolique ou sort du dossier.
     */
    Optional<URI> locate(String root, String relative);
}
