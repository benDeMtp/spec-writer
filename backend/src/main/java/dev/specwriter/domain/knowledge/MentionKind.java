package dev.specwriter.domain.knowledge;

/** Sorte d'un document mentionnable avec @ (007, research.md R2). */
public enum MentionKind {
    /** Document d'un dossier synchronisé (006). */
    DOCUMENT,
    /** Page entière d'une documentation Antora. */
    PAGE,
    ADR
}
