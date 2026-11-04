package dev.specwriter.application.knowledge;

import dev.specwriter.domain.consent.ConsentPolicy;
import dev.specwriter.domain.consent.ConsentService;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.ArchitectureModel.DeploymentFact;
import dev.specwriter.domain.knowledge.ArchitectureModel.Exchange;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeSelection.ImpactedModule;
import dev.specwriter.domain.llm.ImpactRequest;
import dev.specwriter.domain.llm.ImpactResult;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.LlmClient;
import dev.specwriter.domain.session.Language;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Modules impactés, échanges et efforts de déploiement (FR-039 à FR-041, research.md R22) : la
 * recherche propose 15 éléments candidats au plus, l'IA choisit parmi eux ({@code identifyImpact}),
 * et l'outil vérifie sur le modèle l'existence de chaque échange et les constats de production. Un
 * nom hors des candidats est ramené par {@link Citations} ou retiré. Le choix de l'IA est gardé par
 * session tant que le besoin, les réponses et les candidats ne changent pas.
 */
public class AnalyzeArchitecture {

    static final int DESCRIPTION = 600;
    private static final int SESSIONS = 200;

    /** Faits d'architecture d'une session. */
    public record Analysis(List<ImpactedModule> modules, List<Exchange> exchanges, List<DeploymentFact> facts) {

        public static final Analysis EMPTY = new Analysis(List.of(), List.of(), List.of());

        public Analysis {
            modules = List.copyOf(modules);
            exchanges = List.copyOf(exchanges);
            facts = List.copyOf(facts);
        }
    }

    /** Choix de l'IA, noms ramenés aux candidats. */
    private record Choice(String key, List<String> modules, List<List<String>> exchanges) {
    }

    private final KnowledgeIndex index;
    private final LlmClient llm;
    private final ConsentPolicy consent;
    private final Map<UUID, Choice> choices = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Choice> eldest) {
            return size() > SESSIONS;
        }
    };

    public AnalyzeArchitecture(KnowledgeIndex index, LlmClient llm, ConsentPolicy consent) {
        this.index = index;
        this.llm = llm;
        this.consent = consent;
    }

    /**
     * @param query texte de la recherche (besoin et réponses) ; {@code vector} et {@code model} sont
     *     nuls si le modèle d'embedding n'est pas prêt
     * @throws dev.specwriter.domain.error.DomainException si l'appel à l'IA échoue (rien n'est gardé)
     */
    public Analysis run(UUID sessionId, UUID projectId, Language language, String need, List<QuestionAndAnswer> answers,
            String query, float[] vector, String model) {
        List<KnowledgeItem> candidates = index.search(projectId, query, vector, model, List.of(KnowledgeKind.ARCH_ELEMENT),
                ImpactRequest.MAX_CANDIDATES).stream().map(KnowledgeIndex.Hit::item).toList();
        if (candidates.isEmpty() || !consent.isGranted(ConsentService.COPILOT)) {
            return Analysis.EMPTY;
        }
        String key = key(need, answers, candidates);
        Choice choice;
        synchronized (choices) {
            choice = choices.get(sessionId);
        }
        if (choice == null || !choice.key().equals(key)) {
            choice = choose(key, language, need, answers, candidates);
            synchronized (choices) {
                choices.put(sessionId, choice);
            }
        }
        return facts(projectId, choice, candidates);
    }

    private Choice choose(String key, Language language, String need, List<QuestionAndAnswer> answers,
            List<KnowledgeItem> candidates) {
        List<ImpactRequest.Element> elements = candidates.stream().map(c -> new ImpactRequest.Element(c.ref(),
                c.elementType(), excerpt(c.body()), "", c.scope() == null ? "" : c.scope().name())).toList();
        ImpactResult result = llm.identifyImpact(new ImpactRequest(language, need, answers, elements));
        Set<String> names = new LinkedHashSet<>();
        candidates.forEach(c -> names.add(c.ref()));
        Citations.Resolver resolver = Citations.resolver(names);
        Set<String> modules = new LinkedHashSet<>();
        for (String raw : result.modules()) {
            String name = resolver.resolve(raw);
            if (name != null) {
                modules.add(name);
            }
        }
        Set<List<String>> exchanges = new LinkedHashSet<>();
        for (ImpactResult.Exchange e : result.exchanges()) {
            String from = resolver.resolve(e.from());
            String to = resolver.resolve(e.to());
            if (from != null && to != null && !from.equals(to)) {
                exchanges.add(List.of(from, to));
            }
        }
        return new Choice(key, List.copyOf(modules), List.copyOf(exchanges));
    }

    /** Existence des échanges et constats de production, vérifiés sur le modèle actuel. */
    private Analysis facts(UUID projectId, Choice choice, List<KnowledgeItem> candidates) {
        ArchitectureModel model = index.architecture(projectId);
        List<ImpactedModule> modules = new ArrayList<>();
        for (String name : choice.modules()) {
            candidates.stream().filter(c -> c.ref().equals(name)).findFirst()
                    .ifPresent(c -> modules.add(new ImpactedModule(name, c.scope())));
        }
        List<Exchange> exchanges = choice.exchanges().stream().map(p -> model.exchange(p.get(0), p.get(1))).toList();
        return new Analysis(modules, exchanges, model.deploymentFacts(choice.modules(), choice.exchanges()));
    }

    private static String excerpt(String body) {
        return body.length() <= DESCRIPTION ? body : body.substring(0, DESCRIPTION - 1) + "…";
    }

    /** Besoin, réponses et candidats (référence et empreinte) : leur changement relance l'IA. */
    private static String key(String need, List<QuestionAndAnswer> answers, List<KnowledgeItem> candidates) {
        StringBuilder out = new StringBuilder(need);
        answers.forEach(a -> out.append('\u0000').append(a.questionId()).append('=').append(a.answer()));
        candidates.forEach(c -> out.append('\u0001').append(c.ref()).append('|').append(c.contentHash()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(out.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
