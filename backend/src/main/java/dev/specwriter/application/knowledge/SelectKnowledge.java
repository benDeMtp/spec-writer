package dev.specwriter.application.knowledge;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.ArchitectureModel.DeploymentFact;
import dev.specwriter.domain.knowledge.ArchitectureModel.Exchange;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeSelection.ImpactedModule;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.EmbeddingPort;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.session.Session;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Sélection de la connaissance d'une session (research.md R24) : la fiche générale toujours ; le
 * vocabulaire et la vue d'ensemble (3 000 caractères) ; 4 ADR au plus (6 000), un ADR remplacé
 * l'étant par son successeur et un ADR obsolète écarté ; 3 tickets (3 000) ; pour une
 * spécification, les faits d'architecture (3 000). Le tout tient en 35 000 caractères. La recherche
 * part du besoin et des réponses : la sélection suit leurs changements.
 */
public class SelectKnowledge implements SessionKnowledge {

    static final int MAX_ROLES = 3_000;
    static final int MAX_ADRS = 4;
    static final int MAX_ADR_TEXT = 6_000;
    static final int MAX_TICKETS = 3;
    static final int MAX_TICKET_TEXT = 3_000;
    static final int MAX_ARCHITECTURE = 3_000;
    static final int MAX_QUERY = KnowledgeQueries.MAX_QUERY;
    /** Taille comptée pour un module, un échange ou un constat, en plus de ses noms. */
    private static final int ARCHITECTURE_MARKUP = 40;
    private static final int ADR_CANDIDATES = 12;
    private static final int SUCCESSION = 5;
    private static final Pattern ADR_NUMBER = Pattern.compile("adr[-_ ]?0*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static final Logger LOG = Logger.getLogger(SelectKnowledge.class);

    private final ProjectRepository projects;
    private final KnowledgeIndex index;
    private final EmbeddingPort embedding;
    private final AnalyzeArchitecture analyze;

    public SelectKnowledge(ProjectRepository projects, KnowledgeIndex index, EmbeddingPort embedding,
            AnalyzeArchitecture analyze) {
        this.projects = projects;
        this.index = index;
        this.embedding = embedding;
        this.analyze = analyze;
    }

    @Override
    public ProjectKnowledge forQuestions(Session session, List<QuestionAndAnswer> answers) {
        return select(session, answers, false);
    }

    @Override
    public ProjectKnowledge forSpecification(Session session, List<QuestionAndAnswer> answers) {
        return select(session, answers, true);
    }

    private ProjectKnowledge select(Session session, List<QuestionAndAnswer> answers, boolean architecture) {
        ProjectKnowledge sheet = SessionKnowledge.sheet(projects, session);
        String query = query(session.need().text(), answers);
        float[] vector = null;
        if (embedding.state() == EmbeddingPort.State.READY) {
            try {
                vector = embedding.query(query);
            } catch (RuntimeException e) {
                // la recherche se rabat sur les termes (FR-043)
                LOG.warnf("Connaissance : vecteur de la recherche indisponible (%s)", e.getClass().getSimpleName());
            }
        }
        String model = vector == null ? null : embedding.modelId();
        List<ProjectKnowledge.Entry> entries = new ArrayList<>();
        entries.addAll(roles(session));
        entries.addAll(adrs(session, query, vector, model));
        entries.addAll(tickets(session, query, vector, model));
        ProjectKnowledge selected = new ProjectKnowledge(sheet.generalSheet(), sheet.sheetUpdatedAt(), entries,
                List.of(), List.of(), List.of());
        if (!architecture) {
            return selected;
        }
        try {
            AnalyzeArchitecture.Analysis a = analyze.run(session.id(), session.projectId(), session.language(),
                    session.need().text(), answers, query, vector, model);
            return bounded(selected.withArchitecture(a.modules(), a.exchanges(), a.facts()));
        } catch (DomainException e) {
            // la rédaction continue sans les faits d'architecture ; l'analyse sera retentée
            LOG.warnf("Connaissance : analyse d'architecture indisponible (%s)", e.code());
            return selected;
        }
    }

    /** Vocabulaire et vue d'ensemble, toujours transmis (FR-042), dans la limite de 3 000 caractères. */
    private List<ProjectKnowledge.Entry> roles(Session session) {
        List<ProjectKnowledge.Entry> out = new ArrayList<>();
        int left = MAX_ROLES;
        for (KnowledgeItem item : index.withRole(session.projectId())) {
            int room = left - item.title().length();
            if (room <= 0) {
                break;
            }
            ProjectKnowledge.Entry e = entry(item, room);
            out.add(e);
            left -= item.title().length() + e.text().length();
        }
        return out;
    }

    /** ADR en vigueur les plus proches ; un remplacé cède la place à son successeur, un obsolète est écarté (FR-036). */
    private List<ProjectKnowledge.Entry> adrs(Session session, String query, float[] vector, String model) {
        List<KnowledgeIndex.Hit> hits = index.search(session.projectId(), query, vector, model, List.of(KnowledgeKind.ADR),
                ADR_CANDIDATES);
        if (hits.isEmpty()) {
            return List.of();
        }
        List<KnowledgeItem> all = index.all(session.projectId(), KnowledgeKind.ADR);
        Set<String> seen = new LinkedHashSet<>();
        List<KnowledgeItem> chosen = new ArrayList<>();
        for (KnowledgeIndex.Hit hit : hits) {
            Optional<KnowledgeItem> current = inForce(hit.item(), all);
            if (current.isPresent() && seen.add(current.get().ref() + "#" + current.get().chunk())) {
                chosen.add(current.get());
            }
            if (chosen.size() == MAX_ADRS) {
                break;
            }
        }
        return bounded(chosen, MAX_ADR_TEXT / MAX_ADRS);
    }

    private static Optional<KnowledgeItem> inForce(KnowledgeItem adr, List<KnowledgeItem> all) {
        KnowledgeItem current = adr;
        for (int hop = 0; hop < SUCCESSION; hop++) {
            AdrStatus status = current.adrStatus();
            if (status != AdrStatus.SUPERSEDED) {
                return status == AdrStatus.DEPRECATED ? Optional.empty() : Optional.of(current);
            }
            String number = number(current.supersededBy());
            if (number == null) {
                return Optional.empty();
            }
            current = all.stream().filter(a -> number.equals(number(fileName(a.ref())))
                    || number.equals(number(a.title()))).findFirst().orElse(null);
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static String number(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = ADR_NUMBER.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String fileName(String ref) {
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    private List<ProjectKnowledge.Entry> tickets(Session session, String query, float[] vector, String model) {
        List<KnowledgeItem> items = index.search(session.projectId(), query, vector, model, List.of(KnowledgeKind.TICKET),
                MAX_TICKETS).stream().map(KnowledgeIndex.Hit::item).toList();
        return bounded(items, MAX_TICKET_TEXT / MAX_TICKETS);
    }

    private static List<ProjectKnowledge.Entry> bounded(List<KnowledgeItem> items, int each) {
        return items.stream().map(i -> entry(i, Math.max(0, each - i.title().length()))).toList();
    }

    private static ProjectKnowledge.Entry entry(KnowledgeItem item, int room) {
        String body = item.body();
        String text = body.length() <= room ? body : body.substring(0, Math.max(0, room - 1)) + "…";
        String status = switch (item.kind()) {
            case ADR -> item.adrStatus() == null ? null : item.adrStatus().name();
            default -> item.status();
        };
        return new ProjectKnowledge.Entry(item.kind(), item.role(), item.ref(), item.chunk(), item.title(), status,
                item.closed(), text, item.contentHash());
    }

    /** Faits d'architecture dans la limite de 3 000 caractères : modules, puis échanges, puis constats. */
    private static ProjectKnowledge bounded(ProjectKnowledge k) {
        if (architectureSize(k) <= MAX_ARCHITECTURE) {
            return k;
        }
        int left = MAX_ARCHITECTURE;
        List<ImpactedModule> modules = new ArrayList<>();
        for (ImpactedModule m : k.modules()) {
            left -= m.name().length() + ARCHITECTURE_MARKUP;
            if (left < 0) {
                break;
            }
            modules.add(m);
        }
        List<Exchange> exchanges = new ArrayList<>();
        for (Exchange x : k.exchanges()) {
            left -= x.source().length() + x.destination().length() + length(x.via()) + ARCHITECTURE_MARKUP;
            if (left < 0) {
                break;
            }
            exchanges.add(x);
        }
        List<DeploymentFact> facts = new ArrayList<>();
        for (DeploymentFact f : k.deploymentFacts()) {
            left -= f.element().length() + length(f.other()) + ARCHITECTURE_MARKUP;
            if (left < 0) {
                break;
            }
            facts.add(f);
        }
        return k.withArchitecture(modules, exchanges, facts);
    }

    /** Taille comptée des faits d'architecture. */
    static int architectureSize(ProjectKnowledge k) {
        int size = 0;
        for (ImpactedModule m : k.modules()) {
            size += m.name().length() + ARCHITECTURE_MARKUP;
        }
        for (Exchange x : k.exchanges()) {
            size += x.source().length() + x.destination().length() + length(x.via()) + ARCHITECTURE_MARKUP;
        }
        for (DeploymentFact f : k.deploymentFacts()) {
            size += f.element().length() + length(f.other()) + ARCHITECTURE_MARKUP;
        }
        return size;
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }

    /** Besoin puis réponses, dans la limite d'une recherche. */
    static String query(String need, List<QuestionAndAnswer> answers) {
        StringBuilder out = new StringBuilder(need);
        for (QuestionAndAnswer a : answers) {
            out.append('\n').append(a.answer());
        }
        return out.length() <= MAX_QUERY ? out.toString() : out.substring(0, MAX_QUERY);
    }
}
