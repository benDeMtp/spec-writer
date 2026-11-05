package dev.specwriter.domain.port;

import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeRole;
import dev.specwriter.domain.project.DocSource;
import java.util.List;

/**
 * Lecture d'une documentation Antora, par son lien ou son dossier (research.md R3 de 002). Les
 * pages lues restent en mémoire ; elles ne sont jamais conservées ni journalisées (FR-013b).
 */
public interface DocumentationReader {

    /**
     * Page lue : son adresse ({@code ref}), son titre, son texte structuré et ses sections à
     * indexer (research.md R20).
     */
    record Page(String ref, String title, String text, List<Section> sections) {

        public Page {
            sections = sections == null ? List.of() : List.copyOf(sections);
        }

        public Page(String ref, String title, String text) {
            this(ref, title, text, List.of());
        }
    }

    /**
     * Élément indexé d'une page : une section ({@code .sect1}, titre {@code h2}) ou un ADR entier,
     * découpé en morceaux d'environ 1 500 caractères. {@code ref} : adresse de la page avec l'ancre
     * de la section ; {@code adr} : statut lu, pour un ADR seulement.
     */
    record Section(String ref, int chunk, String title, String body, KnowledgeKind kind, AdrStatus.Statement adr,
            KnowledgeRole role) {
    }

    enum UnreadReason {
        UNREACHABLE, NOT_ANTORA, HTTP_ERROR, TOO_LARGE, REDIRECT_OTHER_HOST, LIMIT_REACHED,
        /** Dossier : il n'existe plus. */
        FOLDER_NOT_FOUND,
        /** Dossier : le chemin désigne un fichier. */
        NOT_A_FOLDER,
        /** Dossier, sous-dossier ou fichier que l'utilisateur n'a pas le droit de lire. */
        ACCESS_DENIED,
        /** Fichier qui n'a pas pu être lu pour une autre raison. */
        UNREADABLE,
        /** Dossier sans aucune page HTML. */
        NO_HTML,
        /** Dossier de sources AsciiDoc ({@code .adoc}) au lieu du site généré par Antora. */
        ADOC_SOURCES
    }

    /** Pages annoncées par une source et, si elle n'en annonce aucune, pourquoi. */
    record Availability(int pages, UnreadReason problem) {

        public static Availability of(int pages) {
            return new Availability(pages, null);
        }
    }

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

    /** Pages annoncées par le sitemap ou le dossier, sans les lire (FR-013b), ou le motif de leur absence. */
    Availability count(DocSource source);

    default Result read(DocSource source) {
        return read(source, Listener.NONE);
    }

    Result read(DocSource source, Listener listener);
}
