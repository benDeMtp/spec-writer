package dev.specwriter.domain.port;

import dev.specwriter.domain.sources.ModuleCorrection;
import dev.specwriter.domain.sources.PerimeterSnapshot;
import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.MessageRole;
import dev.specwriter.domain.conversation.NeedDraft;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.conversation.TurnSource;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.DraftSpecification;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.sources.ModuleQualification;
import dev.specwriter.domain.sources.PerimeterAnalysis;
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

    /** Spécification discutée (011) : nom et version transmise, en tête des sources. */
    record Discussed(String name, int version) {
    }

    /**
     * Ce que l'agent sait au moment de choisir : chercher encore, ou répondre. {@code memory} :
     * résumé des échanges anciens (vide s'il n'y en a pas), avant {@code history}, les récents.
     */
    record PlanRequest(Language language, String generalSheet, String memory, List<Exchange> history, String question,
            List<TurnSource> found, int searchesLeft, Discussed discussed, boolean perimeterAvailable,
            List<String> lastPerimeterTerms) {

        public PlanRequest {
            lastPerimeterTerms = lastPerimeterTerms == null ? List.of() : List.copyOf(lastPerimeterTerms);
        }

        public PlanRequest(Language language, String generalSheet, String memory, List<Exchange> history,
                String question, List<TurnSource> found, int searchesLeft, Discussed discussed) {
            this(language, generalSheet, memory, history, question, found, searchesLeft, discussed, false, List.of());
        }

        public PlanRequest(Language language, String generalSheet, String memory, List<Exchange> history,
                String question, List<TurnSource> found, int searchesLeft) {
            this(language, generalSheet, memory, history, question, found, searchesLeft, null);
        }
    }

    /** Chercher dans la connaissance, analyser le périmètre dans les sources du code (013), ou répondre. */
    enum Action {
        SEARCH, PERIMETER, ANSWER
    }

    /** {@code terms} : termes à chercher dans les sources du code, pour l'action {@code PERIMETER}. */
    record Plan(Action action, String query, Set<SourceKind> kinds, List<String> terms) {

        public Plan {
            terms = terms == null ? List.of() : List.copyOf(terms);
        }

        public Plan(Action action, String query, Set<SourceKind> kinds) {
            this(action, query, kinds, List.of());
        }

        public static Plan answer() {
            return new Plan(Action.ANSWER, null, Set.of());
        }

        public static Plan perimeter(List<String> terms) {
            return new Plan(Action.PERIMETER, null, Set.of(), terms);
        }
    }

    /**
     * Données de la qualification d'une analyse de périmètre (013, research.md R7) : la question,
     * l'échange récent et l'analyse, extraits compris.
     */
    record QualifyRequest(Language language, List<Exchange> history, String question, PerimeterAnalysis analysis) {
    }

    /**
     * Données de la réponse : sources numérotées, sources non transmises faute de place, et si la
     * recherche s'est arrêtée au plafond.
     */
    record AnswerRequest(Language language, String generalSheet, String memory, List<Exchange> history,
            String question, List<TurnSource> sources, List<String> notCompared, boolean searchStopped,
            Discussed discussed, boolean perimeterUnavailable) {

        public AnswerRequest(Language language, String generalSheet, String memory, List<Exchange> history,
                String question, List<TurnSource> sources, List<String> notCompared, boolean searchStopped,
                Discussed discussed) {
            this(language, generalSheet, memory, history, question, sources, notCompared, searchStopped, discussed, false);
        }

        public AnswerRequest(Language language, String generalSheet, String memory, List<Exchange> history,
                String question, List<TurnSource> sources, List<String> notCompared, boolean searchStopped) {
            this(language, generalSheet, memory, history, question, sources, notCompared, searchStopped, null);
        }
    }

    /** Échange et sources citées dans la conversation, pour rédiger un besoin (010, US2). */
    record DraftRequest(Language language, String generalSheet, String memory, List<Exchange> history,
            List<CitedSource> cited) {
    }

    /** Échanges anciens à résumer, avec le résumé précédent (vide au premier compactage). */
    record CompactRequest(Language language, String previous, List<Exchange> exchanges) {
    }

    /**
     * Tour de la discussion transmis pour une proposition (011) : {@code ref}
     * ({@code discussion:<conversation>#<numéro>}) sur les messages de l'utilisateur, qui peuvent
     * être cités ; nul sur les réponses de l'agent.
     */
    record DiscussionLine(MessageRole role, String text, String ref) {
    }

    /**
     * Données d'une nouvelle version proposée d'après la discussion (011, research.md R4) : version
     * courante, besoin et réponses, connaissance du projet, mémoire et échanges de la discussion,
     * sources qu'elle a citées.
     */
    record ReviseRequest(Language language, String need, List<QuestionAndAnswer> answers, String currentMarkdown,
            ProjectKnowledge knowledge, String memory, List<DiscussionLine> discussion, List<CitedSource> cited,
            String correction, PerimeterSnapshot perimeter) {

        public ReviseRequest {
            answers = List.copyOf(answers);
            discussion = List.copyOf(discussion);
            cited = List.copyOf(cited);
            knowledge = knowledge == null ? ProjectKnowledge.EMPTY : knowledge;
        }

        /** Sans périmètre analysé (013) : les modules ne se corrigent pas. */
        public ReviseRequest(Language language, String need, List<QuestionAndAnswer> answers, String currentMarkdown,
                ProjectKnowledge knowledge, String memory, List<DiscussionLine> discussion, List<CitedSource> cited,
                String correction) {
            this(language, need, answers, currentMarkdown, knowledge, memory, discussion, cited, correction, null);
        }

        public ReviseRequest withCorrection(String newCorrection) {
            return new ReviseRequest(language, need, answers, currentMarkdown, knowledge, memory, discussion, cited,
                    newCorrection, perimeter);
        }
    }

    /**
     * {@code draft} est présent si {@code changed} ; sinon {@code reason} dit pourquoi rien ne change.
     * {@code perimeter} : qualifications de modules que la discussion a établies (013), même sans
     * autre changement.
     */
    record Revision(boolean changed, String reason, DraftSpecification draft, List<ModuleCorrection> perimeter) {

        public Revision {
            perimeter = perimeter == null ? List.of() : List.copyOf(perimeter);
        }

        public Revision(boolean changed, String reason, DraftSpecification draft) {
            this(changed, reason, draft, List.of());
        }
    }

    /** Données du relevé des changements discutés : version courante, mémoire et échanges (011). */
    record ChangesRequest(Language language, String currentMarkdown, String memory, List<DiscussionLine> discussion) {

        public ChangesRequest {
            discussion = List.copyOf(discussion);
        }
    }

    /** Qui a avancé un changement : la personne, ou l'agent sans confirmation de la personne. */
    enum ChangeOrigin {
        USER,
        AGENT
    }

    /** Changement de la spécification repéré dans la discussion, à confirmer par la personne. */
    record ChangeCandidate(String text, ChangeOrigin origin) {

        public static final int MAX_TEXT = 300;
        public static final int MAX_CHANGES = 10;
    }

    /**
     * Exécute {@code work} ; les appels qu'il fait sur ce fil transmettent à {@code reasoning} la
     * réflexion du modèle, au fil de l'eau, quand le modèle en donne une. Sans réflexion disponible,
     * {@code work} s'exécute simplement.
     */
    default void thinking(Consumer<String> reasoning, Runnable work) {
        work.run();
    }

    Plan plan(PlanRequest request);

    /**
     * Qualifie les modules d'une analyse de périmètre (013) : impacté, à vérifier ou non impacté,
     * avec une justification. Un module omis reste non qualifié ; aucun n'est retiré.
     */
    List<ModuleQualification> qualify(QualifyRequest request);

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

    /**
     * Réécrit la spécification d'après la discussion : ne change que ce que la discussion a établi,
     * ou dit qu'elle n'a établi aucun changement (011, FR-007, FR-009).
     */
    Revision revise(ReviseRequest request);

    /**
     * Relève les changements de la spécification que la discussion a avancés, pour que la personne
     * coche ceux à intégrer avant la rédaction ; une liste vide si la discussion n'en avance aucun.
     */
    List<ChangeCandidate> changes(ChangesRequest request);
}
