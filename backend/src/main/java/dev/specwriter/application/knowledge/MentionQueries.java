package dev.specwriter.application.knowledge;

import dev.specwriter.application.specification.DraftValidation;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.Mention;
import dev.specwriter.domain.knowledge.MentionField;
import dev.specwriter.domain.knowledge.MentionKind;
import dev.specwriter.domain.knowledge.Mentionable;
import dev.specwriter.domain.knowledge.Mentions;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.session.Session;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mentions @ (007) : suggestions pendant la frappe et état de chaque mention avant la génération.
 * Tout vient de l'index local : aucun appel externe, aucun fichier lu (FR-016).
 */
public class MentionQueries {

    public static final int MAX_SUGGESTIONS = 8;
    public static final int MAX_QUERY = 200;

    /** Suggestion : sorte, nom, chemin, et la mention à insérer. */
    public record Suggestion(MentionKind kind, String name, String path, String insert) {
    }

    /** Texte d'un champ en cours de saisie. */
    public record FieldText(MentionField field, String text) {
    }

    public enum State {
        TRANSMITTED, PARTIAL, NO_ROOM, AMBIGUOUS, NOT_FOUND, SUPERSEDED, DEPRECATED
    }

    /** Document montré dans un état : sorte, titre, chemin, mention sans ambiguïté. */
    public record Document(MentionKind kind, String title, String path, String insert) {
    }

    /** État d'une mention (research.md R8) ; {@code transmittedShare} est une estimation sur l'index. */
    public record MentionState(String text, MentionField field, State state, Document document,
            List<Document> candidates, int transmittedShare) {
    }

    private final ProjectRepository projects;
    private final SessionRepository sessions;
    private final KnowledgeIndex index;
    private final SelectKnowledge select;

    public MentionQueries(ProjectRepository projects, SessionRepository sessions, KnowledgeIndex index,
            SelectKnowledge select) {
        this.projects = projects;
        this.sessions = sessions;
        this.index = index;
        this.select = select;
    }

    /**
     * Suggestions pour la saisie qui suit @ (research.md R5) : les noms qui commencent par elle,
     * puis ceux qui la contiennent, puis les chemins ; ADR et documents avant les pages, puis
     * l'ordre alphabétique ; {@link #MAX_SUGGESTIONS} au plus.
     */
    public List<Suggestion> suggest(UUID projectId, String query) {
        Project project = requireProject(projectId);
        String q = query == null ? "" : Mentionable.key(query.strip());
        if (q.isEmpty() || query.length() > MAX_QUERY) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "La recherche doit faire entre 1 et 200 caractères.");
        }
        List<Mentionable> catalogue = index.mentionables(project.id());
        Map<String, Integer> owners = owners(catalogue);
        record Ranked(Mentionable m, int rank) {
        }
        List<Ranked> ranked = new ArrayList<>();
        for (Mentionable m : catalogue) {
            int rank = rank(m, q);
            if (rank >= 0) {
                ranked.add(new Ranked(m, rank));
            }
        }
        return ranked.stream()
                .sorted(Comparator.comparingInt(Ranked::rank)
                        .thenComparingInt(r -> r.m().kind() == MentionKind.PAGE ? 1 : 0)
                        .thenComparing(r -> Mentionable.key(r.m().name())).thenComparing(r -> r.m().path()))
                .limit(MAX_SUGGESTIONS)
                .map(r -> new Suggestion(r.m().kind(), r.m().name(), r.m().path(), insert(r.m(), owners))).toList();
    }

    /**
     * État de chaque mention des champs reçus (research.md R8). Avec une session, son besoin et ses
     * réponses comptent dans l'ordre de priorité et dans la place ; un champ reçu remplace le besoin,
     * ou s'ajoute aux réponses et à l'instruction.
     */
    public List<MentionState> resolve(UUID projectId, UUID sessionId, List<FieldText> fields) {
        Project project = requireProject(projectId);
        String need = null;
        List<String> answers = new ArrayList<>();
        if (sessionId != null) {
            Session session = sessions.find(sessionId).filter(s -> projectId.equals(s.projectId()))
                    .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND));
            need = session.need().text();
            sessions.questionnaire(sessionId).ifPresent(q -> DraftValidation.answers(q.treated()).stream()
                    .map(QuestionAndAnswer::answer).forEach(answers::add));
        }
        String instruction = null;
        for (FieldText f : fields) {
            switch (f.field()) {
                case NEED -> need = f.text();
                case ANSWER -> answers.add(f.text());
                case INSTRUCTION -> instruction = f.text();
            }
        }
        if (!Mentions.mayContain(fields.stream().map(FieldText::text).toArray(String[]::new))) {
            return List.of();
        }
        List<Mentionable> catalogue = index.mentionables(project.id());
        Map<String, Integer> owners = owners(catalogue);
        Map<String, MentionState> planned = plan(project, Mentions.designated(need, answers, instruction, catalogue),
                owners);
        List<MentionState> out = new ArrayList<>();
        for (FieldText f : fields) {
            for (Mention m : Mentions.find(f.text(), f.field(), catalogue)) {
                out.add(state(m, planned, owners));
            }
        }
        return out;
    }

    private MentionState state(Mention m, Map<String, MentionState> planned, Map<String, Integer> owners) {
        if (m.notFound()) {
            return new MentionState(m.text(), m.field(), State.NOT_FOUND, null, List.of(), 0);
        }
        if (m.ambiguous()) {
            return new MentionState(m.text(), m.field(), State.AMBIGUOUS, null,
                    m.matches().stream().map(d -> document(d, owners)).toList(), 0);
        }
        MentionState p = planned.get(m.matches().getFirst().ref());
        return new MentionState(m.text(), m.field(), p.state(), p.document(), List.of(), p.transmittedShare());
    }

    /** Place de chaque document désigné, dans l'ordre et avec les règles de la génération. */
    private Map<String, MentionState> plan(Project project, List<Mentionable> designated, Map<String, Integer> owners) {
        ProjectContext context = project.context();
        int left = select.mentionBudget(project.id(), context.markdown());
        List<KnowledgeItem> adrs = designated.stream().anyMatch(d -> d.kind() == MentionKind.ADR)
                ? index.all(project.id(), KnowledgeKind.ADR) : List.of();
        Map<String, MentionState> out = new HashMap<>();
        int transmitted = 0;
        boolean full = false;
        for (Mentionable d : designated) {
            State special = null;
            Mentionable shown = d;
            if (d.kind() == MentionKind.ADR) {
                Optional<KnowledgeItem> head = adrs.stream().filter(a -> a.ref().equals(d.ref()) && a.chunk() == 0)
                        .findFirst();
                Optional<KnowledgeItem> current = head.flatMap(a -> SelectKnowledge.inForce(a, adrs));
                if (current.isEmpty()) {
                    out.put(d.ref(), new MentionState(null, null, State.DEPRECATED, document(d, owners), List.of(), 0));
                    continue;
                }
                if (!current.get().ref().equals(d.ref())) {
                    special = State.SUPERSEDED;
                    shown = Mentionable.of(KnowledgeKind.ADR, current.get().ref(), current.get().title(),
                            adrs.stream().filter(a -> a.ref().equals(current.get().ref())).mapToInt(a -> a.body().length())
                                    .sum());
                }
            }
            int size = shown.title().length() + shown.size();
            int part = SelectKnowledge.mentionPart(transmitted == 0, left);
            int sent = Math.min(size, part);
            if (full || transmitted == SelectKnowledge.MAX_MENTIONED || sent <= 0 || sent > left) {
                full = true;
                out.put(d.ref(), new MentionState(null, null, State.NO_ROOM, document(shown, owners), List.of(), 0));
                continue;
            }
            transmitted++;
            left -= sent;
            int share = size == 0 ? 100 : (int) Math.round(100.0 * sent / size);
            State state = special != null ? special : share >= 100 ? State.TRANSMITTED : State.PARTIAL;
            out.put(d.ref(), new MentionState(null, null, state, document(shown, owners), List.of(), share));
        }
        return out;
    }

    /** Rang d'un document pour la saisie : 0 un nom commence par elle, 1 un nom la contient, 2 le chemin ; -1 sinon. */
    private static int rank(Mentionable m, String q) {
        int best = -1;
        for (String name : m.names()) {
            String key = Mentionable.key(name);
            int r = key.startsWith(q) ? 0 : key.contains(q) ? 1 : -1;
            if (r >= 0 && (best < 0 || r < best)) {
                best = r;
            }
        }
        return best >= 0 ? best : Mentionable.key(m.path()).contains(q) ? 2 : -1;
    }

    /** Nombre de documents qui portent chaque nom par défaut, pour proposer le chemin s'il est partagé. */
    private static Map<String, Integer> owners(List<Mentionable> catalogue) {
        Map<String, Integer> out = new HashMap<>();
        catalogue.forEach(m -> out.merge(Mentionable.key(m.name()), 1, Integer::sum));
        return out;
    }

    private static String insert(Mentionable m, Map<String, Integer> owners) {
        return owners.getOrDefault(Mentionable.key(m.name()), 0) > 1 ? Mentions.rewrite(m) : "@" + m.name();
    }

    private static Document document(Mentionable m, Map<String, Integer> owners) {
        return new Document(m.kind(), m.title(), m.path(), insert(m, owners));
    }

    private Project requireProject(UUID projectId) {
        return projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
