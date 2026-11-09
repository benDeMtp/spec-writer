package dev.specwriter.domain.port;

import dev.specwriter.domain.port.DocumentationReader.UnreadReason;

/**
 * Lecture d'un document joint (009, research.md R2) : son contenu est extrait avec les lecteurs et
 * les bornes de la 006 ; rien du fichier n'est gardé, seulement le texte rendu.
 */
public interface AttachmentReader {

    /** Titre (éventuellement vide) et texte extraits, ou la raison du refus. */
    record Reading(String title, String text, UnreadReason failure) {

        public static Reading of(String title, String text) {
            return new Reading(title, text, null);
        }

        public static Reading failed(UnreadReason reason) {
            return new Reading(null, null, reason);
        }

        public boolean ok() {
            return failure == null;
        }

        @Override
        public String toString() {
            return ok() ? "Reading[" + (text == null ? 0 : text.length()) + " caractères]" : "Reading[" + failure + "]";
        }
    }

    /** {@code fileName} sert au format ; {@code content} est le contenu brut du fichier. */
    Reading read(String fileName, byte[] content);
}
