package dev.specwriter.domain.port;

import java.util.List;

/**
 * Calcul local des vecteurs de la recherche par le sens (research.md R19). Sans modèle prêt, la
 * recherche se fait par les termes seuls, signalée (FR-043).
 */
public interface EmbeddingPort {

    enum State {
        MISSING,
        DOWNLOADING,
        READY,
        ERROR,
        TERMS_ONLY
    }

    /** Avancement d'un téléchargement du modèle, en octets. */
    interface Progress {
        void advanced(long done, long total);

        boolean cancelled();
    }

    State state();

    /** Motif du dernier échec du modèle, sans contenu ; {@code null} s'il n'y en a pas. */
    default String problem() {
        return null;
    }

    /** Nom et révision du modèle ; un autre identifiant impose la revectorisation. */
    String modelId();

    /** Modèle utilisable sans réseau : dossier déclaré présent, ou modèle par défaut déjà téléchargé. */
    default boolean availableLocally() {
        return false;
    }

    /** Télécharge ou charge le modèle si besoin ; renvoie l'état obtenu. */
    State prepare(Progress progress);

    /** Vecteur normalisé d'un besoin ou d'une recherche. */
    float[] query(String text);

    /** Vecteurs normalisés d'éléments indexés, dans l'ordre des textes. */
    List<float[]> passages(List<String> texts);
}
