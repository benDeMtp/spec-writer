package dev.specwriter.domain.knowledge;

import java.util.OptionalInt;

/**
 * Place de la connaissance transmise à l'IA, en caractères (008, research.md R2). {@link #STANDARD}
 * reprend les plafonds d'avant la 008 : c'est le minimum, et la valeur pour un modèle de capacité
 * inconnue. Pour un modèle plus grand, chaque plafond grandit dans la même proportion que le total,
 * borné à {@link #MAX_TOTAL} ; les nombres d'éléments ne changent pas.
 */
public record KnowledgeBudget(int total, int roles, int adrText, int adrPart, int documentText, int documentPart,
        int mentionedText, int mentionedPart, int ticketText, int architecture, int subtaskText,
        int conversationSources, int conversationHistory) {

    public static final KnowledgeBudget STANDARD = new KnowledgeBudget(35_000, 3_000, 12_000, 8_000, 8_000, 6_000,
            12_000, 8_000, 3_000, 3_000, 1_500, 20_000, 8_000);
    /** Extrait d'un résultat de recherche transmis à l'agent (010, research.md R3). */
    public static final int SOURCE_EXCERPT = 1_500;
    /** Plafond du total, pour ne pas noyer le besoin (FR-005). */
    public static final int MAX_TOTAL = 200_000;
    /** Conversion prudente pour le français. */
    static final int CHARS_PER_TOKEN = 3;
    /** Consignes, besoin, réponses, version en cours d'une modification et marge. */
    static final int RESERVE = 60_000;

    /** Budget pour une limite de prompt donnée ; inconnue ou petite : {@link #STANDARD}. */
    public static KnowledgeBudget forPromptTokens(OptionalInt promptTokens) {
        if (promptTokens.isEmpty() || promptTokens.getAsInt() <= 0) {
            return STANDARD;
        }
        long available = (long) promptTokens.getAsInt() * CHARS_PER_TOKEN - RESERVE;
        long total = Math.min(MAX_TOTAL, available / 2);
        if (total <= STANDARD.total) {
            return STANDARD;
        }
        double factor = (double) total / STANDARD.total;
        return new KnowledgeBudget((int) total, scale(STANDARD.roles, factor), scale(STANDARD.adrText, factor),
                scale(STANDARD.adrPart, factor), scale(STANDARD.documentText, factor),
                scale(STANDARD.documentPart, factor), scale(STANDARD.mentionedText, factor),
                scale(STANDARD.mentionedPart, factor), scale(STANDARD.ticketText, factor),
                scale(STANDARD.architecture, factor), scale(STANDARD.subtaskText, factor),
                scale(STANDARD.conversationSources, factor), scale(STANDARD.conversationHistory, factor));
    }

    public boolean isStandard() {
        return equals(STANDARD);
    }

    private static int scale(int base, double factor) {
        return (int) Math.floor(base * factor);
    }
}
