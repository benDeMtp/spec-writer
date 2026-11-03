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

    /** Page ou source non lue, avec son motif. */
    record Unread(String location, UnreadReason reason) {
    }

    record Result(List<Page> pages, List<Unread> unread) {

        public boolean limitReached() {
            return unread.stream().anyMatch(u -> u.reason() == UnreadReason.LIMIT_REACHED);
        }
    }

    /** Nombre de pages annoncées par le sitemap ou le dossier, sans les lire (FR-013b). */
    int count(DocSource source);

    Result read(DocSource source);
}
