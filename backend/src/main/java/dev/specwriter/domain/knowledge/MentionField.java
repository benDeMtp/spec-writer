package dev.specwriter.domain.knowledge;

/** Champ où une mention @ est écrite (007) ; l'ordre donne la priorité de transmission. */
public enum MentionField {
    NEED,
    ANSWER,
    INSTRUCTION,
    /** Document joint à la session (009) : pas un champ de saisie, un document désigné par son ajout. */
    ATTACHMENT
}
