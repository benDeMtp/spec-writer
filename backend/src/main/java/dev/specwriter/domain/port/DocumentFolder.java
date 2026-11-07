package dev.specwriter.domain.port;

import dev.specwriter.domain.port.DocumentationReader.Page;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.project.DocSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Dossier de documents (006) : Word, Excel, PDF et HTML d'un dossier local, par exemple un dossier
 * SharePoint synchronisé par OneDrive. Rien n'est copié ni conservé : le parcours ne lit que les noms
 * et les attributs, la lecture extrait le texte d'un document à la demande.
 */
public interface DocumentFolder {

    /** Limites par fichier et par source (research.md R7). */
    record Limits(int maxDocuments, Duration maxDuration, long maxFileBytes, int maxChars, Duration fileTimeout) {
        public static final Limits DEFAULT = new Limits(2_000, Duration.ofMinutes(10), 20L * 1024 * 1024, 200_000,
                Duration.ofSeconds(60));
    }

    /** Document retenu : chemin relatif ({@code /}), taille et date de modification. */
    record FileEntry(String path, long size, Instant modifiedAt) {
    }

    /**
     * Parcours du dossier : documents retenus (triés par chemin), exclus par un motif, ignorés pour
     * leur format ; {@code problem} si le dossier lui-même est illisible.
     */
    record Scan(List<FileEntry> retained, List<String> excluded, List<String> ignored, boolean limitReached,
            UnreadReason problem) {
    }

    /** Document lu, ou motif de l'échec ({@code PROTECTED}, {@code NO_TEXT}, {@code TOO_LARGE}…). */
    record Reading(Page page, UnreadReason failure) {
        public static Reading of(Page page) {
            return new Reading(page, null);
        }

        public static Reading failed(UnreadReason reason) {
            return new Reading(null, reason);
        }
    }

    Scan scan(DocSource source);

    Reading read(DocSource source, String path);

    /** Préfixe des références des éléments d'une source : {@code docs:<id>/}. */
    static String prefix(DocSource source) {
        return "docs:" + source.id() + "/";
    }

    /** Référence d'un document : {@code docs:<id>/<chemin>}. */
    static String ref(DocSource source, String path) {
        return prefix(source) + path;
    }
}
