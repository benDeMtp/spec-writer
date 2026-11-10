package dev.specwriter.domain.port;

import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.conversation.NeedDraft;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.conversation.TurnSource;
import dev.specwriter.domain.session.Language;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Agent conversationnel (010, research.md R1, R2) : chaque appel reçoit des données seulement ; les
 * consignes sont celles de l'outil. Erreurs en {@code DomainException} comme {@link LlmClient}.
 */
public interface ConversationLlm {

    /** Un tour de parole transmis comme historique. */
    record Exchange(MessageRole role, String text) {
    }

    /**
     * Ce que l'agent sait au moment de choisir : chercher encore, ou répondre. {@code memory} :
     * résumé des échanges anciens (vide s'il n'y en a pas), avant {@code history}, les récents.
     */
    record PlanRequest(Language language, String generalSheet, String memory, List<Exchange> history, String question,
            List<TurnSource> found, int searchesLeft) {
    }

    enum Action {
        SEARCH, ANSWER
    }

    record Plan(Action action, String query, Set<SourceKind> kinds) {

        public static Plan answer() {
            return new Plan(Action.ANSWER, null, Set.of());
        }
    }

    /**
     * Données de la réponse : sources numérotées, sources non transmises faute de place, et si la
     * recherche s'est arrêtée au plafond.
     */
    record AnswerRequest(Language language, String generalSheet, String memory, List<Exchange> history,
            String question, List<TurnSource> sources, List<String> notCompared, boolean searchStopped) {
    }

    /** Échange et sources citées dans la conversation, pour rédiger un besoin (010, US2). */
    record DraftRequest(Language language, String generalSheet, String memory, List<Exchange> history,
            List<CitedSource> cited) {
    }

    /** Échanges anciens à résumer, avec le résumé précédent (vide au premier compactage). */
    record CompactRequest(Language language, String previous, List<Exchange> exchanges) {
    }

    Plan plan(PlanRequest request);

    /**
     * Rédige la réponse en Markdown en flux : chaque morceau est passé à {@code deltas} ; s'arrête
     * dès que {@code cancelled} est vrai. Renvoie le texte complet (ou ce qui a été écrit).
     */
    String answer(AnswerRequest request, Consumer<String> deltas, BooleanSupplier cancelled);

    /** Propose un besoin et des points établis ; rien n'est démarré. */
    NeedDraft draftNeed(DraftRequest request);

    /**
     * Résume les échanges anciens dans la mémoire de la conversation : faits établis, décisions,
     * questions ouvertes ; renvoie le nouveau résumé, qui remplace le précédent.
     */
    String compact(CompactRequest request);
}
