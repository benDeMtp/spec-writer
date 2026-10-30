package dev.specwriter.domain.question;

/** États d'une question (data-model.md, Question). Les questions OBSOLETE ne sont plus affichées. */
public enum QuestionState {
    PENDING,
    ANSWERED,
    SKIPPED,
    OBSOLETE
}
