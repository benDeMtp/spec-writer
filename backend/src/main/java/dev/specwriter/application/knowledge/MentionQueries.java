package dev.specwriter.application.knowledge;

import dev.specwriter.application.attachment.AttachmentDrafts;
import dev.specwriter.application.specification.DraftValidation;
import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.knowledge.KnowledgeBudget;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.Mention;
import dev.specwriter.domain.knowledge.MentionField;
import dev.specwriter.domain.knowledge.MentionKind;
import dev.specwriter.domain.knowledge.Mentionable;
import dev.specwriter.domain.knowledge.Mentions;
import dev.specwriter.domain.llm.QuestionAndAnswer;
import dev.specwriter.domain.port.AttachmentRepository;
import dev.specwriter.domain.port.KnowledgeIndex;
import dev.specwriter.domain.port.ProjectRepository;
import dev.specwriter.domain.port.SessionRepository;
import dev.specwriter.domain.project.Project;
import dev.specwriter.domain.project.ProjectContext;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.settings.ModelCapacity;
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

    /** Modèle autorisé qui transmettrait le document en entier (008, research.md R5). */
    public record BetterModel(String id, String name, Double multiplier) {
    }

    /**
     * État d'une mention (research.md R8) ; {@code transmittedShare} est une estimation sur l'index.
     * Pour une mention coupée, {@code betterModel} est le modèle le moins coûteux qui la transmettrait
     * en entier, s'il y en a un (008).
     */
    public record MentionState(String text, MentionField field, State state, Document document,
            List<Document> candidates, int transmittedShare, BetterModel betterModel) {

        public MentionState(String text, MentionField field, State state, Document document, List<Document> candidates,
                int transmittedShare) {
            this(text, field, state, document, candidates, transmittedShare, null);
        }

        MentionState withBetterModel(BetterModel better) {
            return new MentionState(text, field, state, document, candidates, transmittedShare, better);
        }
    }

    private final ProjectRepository projects;
    private final SessionRepository sessions;
    private final KnowledgeIndex index;
    private final SelectKnowledge select;
    /** Documents joints avant le démarrage et ceux des sessions (009) ; null : aucun. */
    private final AttachmentDrafts drafts;
    private final AttachmentRepository attachments;

    public MentionQueries(ProjectRepository projects, SessionRepository sessions, KnowledgeIndex index,
            SelectKnowledge select) {
        this(projects, sessions, index, select, null, null);
    }

    public MentionQueries(ProjectRepository projects, SessionRepository sessions, KnowledgeIndex index,
            SelectKnowledge select, AttachmentDrafts drafts, AttachmentRepository attachments) {
        this.drafts = drafts;
        this.attachments = attachments;
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
        return resolve(projectId, sessionId, fields, List.of());
    }

    /**
     * Comme {@link #resolve(UUID, UUID, List)}, avec les documents joints (009) : ceux de la session,
     * puis les brouillons reçus (un brouillon expiré est ignoré). Chacun a un état de champ
     * {@code ATTACHMENT}, placé après les mentions du besoin, dans l'ordre de transmission.
     */
    public List<MentionState> resolve(UUID projectId, UUID sessionId, List<FieldText> fields, List<UUID> attachmentIds) {
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
                default -> {
                    // ATTACHMENT n'est pas un champ saisi (refusé par la ressource)
                }
            }
        }
        List<Mentionable> joined = joined(sessionId, attachmentIds);
        boolean written = Mentions.mayContain(fields.stream().map(FieldText::text).toArray(String[]::new));
        if (!written && joined.isEmpty()) {
            return List.of();
        }
        List<Mentionable> catalogue = written ? index.mentionables(project.id()) : List.of();
        Map<String, Integer> owners = owners(catalogue);
        List<Mentionable> designated = SelectKnowledge.designatedOrder(need, answers, instruction, catalogue, joined);
        KnowledgeBudget budget = select.budget();
        Map<String, MentionState> planned = better(project, designated, owners, budget,
                plan(project, designated, owners, budget));
        List<MentionState> out = new ArrayList<>();
        for (FieldText f : fields.stream().filter(f -> f.field() == MentionField.NEED).toList()) {
            Mentions.find(f.text(), f.field(), catalogue).forEach(m -> out.add(state(m, planned, owners)));
        }
        for (Mentionable j : joined) {
            MentionState p = planned.get(j.ref());
            out.add(new MentionState(j.title(), MentionField.ATTACHMENT, p.state(), p.document(), List.of(),
                    p.transmittedShare(), p.betterModel()));
        }
        for (FieldText f : fields.stream().filter(f -> f.field() != MentionField.NEED).toList()) {
            Mentions.find(f.text(), f.field(), catalogue).forEach(m -> out.add(state(m, planned, owners)));
        }
        return out;
    }

    /** Documents joints de la session puis brouillons reçus, sans leur texte. */
    private List<Mentionable> joined(UUID sessionId, List<UUID> attachmentIds) {
        List<Mentionable> out = new ArrayList<>();
        if (sessionId != null && attachments != null) {
            attachments.views(sessionId).forEach(v -> out.add(SelectKnowledge.joined(Attachment.REF_PREFIX + v.fileName(),
                    v.title(), v.fileName(), v.chars())));
        }
        if (drafts != null && attachmentIds != null && !attachmentIds.isEmpty()) {
            drafts.find(attachmentIds).forEach(d -> out.add(SelectKnowledge.joined(d.ref(), d.title(), d.fileName(),
                    d.text().length())));
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
        return new MentionState(m.text(), m.field(), p.state(), p.document(), List.of(), p.transmittedShare(),
                p.betterModel());
    }

    /**
     * Pour chaque mention coupée (en partie ou sans place), le modèle autorisé le moins coûteux qui la
     * transmettrait en entier : coefficient inconnu compté comme le plus cher, puis la plus petite
     * limite (research.md R5). Rien n'est changé dans les réglages.
     */
    private Map<String, MentionState> better(Project project, List<Mentionable> designated, Map<String, Integer> owners,
            KnowledgeBudget current, Map<String, MentionState> planned) {
        List<String> cut = planned.entrySet().stream()
                .filter(e -> e.getValue().state() == State.PARTIAL || e.getValue().state() == State.NO_ROOM)
                .map(Map.Entry::getKey).toList();
        if (cut.isEmpty()) {
            return planned;
        }
        List<ModelCapacity> candidates = select.models().stream().filter(c -> c.promptTokens() != null)
                .sorted(Comparator.comparingDouble(ModelCapacity::cost).thenComparingInt(ModelCapacity::promptTokens))
                .toList();
        Map<String, MentionState> out = new HashMap<>(planned);
        Map<String, BetterModel> found = new HashMap<>();
        for (ModelCapacity c : candidates) {
            KnowledgeBudget budget = KnowledgeBudget.forPromptTokens(c.prompt());
            if (budget.total() <= current.total()) {
                continue;
            }
            Map<String, MentionState> other = plan(project, designated, owners, budget);
            for (String ref : cut) {
                MentionState there = other.get(ref);
                if (!found.containsKey(ref) && there != null && there.state() != State.NO_ROOM
                        && there.transmittedShare() >= 100) {
                    found.put(ref, new BetterModel(c.id(), c.name(), c.multiplier()));
                }
            }
            if (found.size() == cut.size()) {
                break;
            }
        }
        found.forEach((ref, model) -> out.put(ref, out.get(ref).withBetterModel(model)));
        return out;
    }

    /** Place de chaque document désigné, dans l'ordre et avec les règles de la génération. */
    private Map<String, MentionState> plan(Project project, List<Mentionable> designated, Map<String, Integer> owners,
            KnowledgeBudget budget) {
        ProjectContext context = project.context();
        int left = select.mentionBudget(project.id(), context.markdown(), budget);
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
            int part = SelectKnowledge.mentionPart(budget, transmitted == 0, left);
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
        return new Document(m.kind(), m.title(), m.path(), m.kind() == MentionKind.ATTACHMENT ? null : insert(m, owners));
    }

    private Project requireProject(UUID projectId) {
        return projects.find(projectId).orElseThrow(() -> new DomainException(ErrorCode.PROJECT_NOT_FOUND));
    }
}
