package dev.specwriter.domain.port;

import dev.specwriter.domain.project.DocSource;
import java.util.List;

/**
 * Lecture d'une documentation Antora, par son lien ou son dossier (research.md R3 de 002). Les
 * pages lues restent en mémoire ; elles ne sont jamais conservées ni journalisées (FR-013b).
 */
public interface DocumentationReader {

    /** Page lue : son adresse ({@code ref}, citée par la fiche), son titre et son texte structuré. */
    record Page(String ref, String title, String text) {
    }

    enum UnreadReason { UNREACHABLE, NOT_ANTORA, HTTP_ERROR, TOO_LARGE, REDIRECT_OTHER_HOST, LIMIT_REACHED }

    /** Page ou source non lue, avec son motif et le code HTTP reçu ({@code null} sans réponse). */
    record Unread(String location, UnreadReason reason, Integer httpStatus) {

        public Unread(String location, UnreadReason reason) {
            this(location, reason, null);
        }
    }

    record Result(List<Page> pages, List<Unread> unread) {

        public boolean limitReached() {
            return unread.stream().anyMatch(u -> u.reason() == UnreadReason.LIMIT_REACHED);
        }
    }

    /**
     * Suivi d'une lecture (FR-013f) : appelé avant chaque page, éventuellement depuis plusieurs
     * threads. Une lecture interrompue s'arrête avant la page suivante et renvoie ce qui a été lu.
     */
    interface Listener {

        Listener NONE = (location, done, total) -> { };

        /** {@code done} pages déjà traitées sur {@code total} connues (0 si encore inconnu). */
        void reading(String location, int done, int total);

        default boolean cancelled() {
            return false;
        }
    }

    /** Nombre de pages annoncées par le sitemap ou le dossier, sans les lire (FR-013b). */
    int count(DocSource source);

    default Result read(DocSource source) {
        return read(source, Listener.NONE);
    }

    Result read(DocSource source, Listener listener);
}
