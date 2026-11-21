package fr.kawamind.application.lifecycle;

import fr.kawamind.application.conversation.TurnEvents;
import fr.kawamind.application.knowledge.Indexations;
import fr.kawamind.domain.conversation.Conversation;
import fr.kawamind.domain.port.ConversationRepository;
import fr.kawamind.domain.port.ProjectRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Travaux en cours, à nommer avant Quitter et à interrompre à l'arrêt (019, research.md R4) :
 * indexations, tours de conversation, générations synchrones. Seuls des noms sortent d'ici, jamais
 * de contenu saisi.
 */
public class RunningWork {

    /** {@code projectName} et {@code conversationTitle} sont absents quand ils ne s'appliquent pas. */
    public record Item(WorkKind kind, String projectName, String conversationTitle) {
    }

    private final Indexations indexations;
    private final TurnEvents turns;
    private final LongWorkTracker generations;
    private final ProjectRepository projects;
    private final ConversationRepository conversations;

    public RunningWork(Indexations indexations, TurnEvents turns, LongWorkTracker generations,
            ProjectRepository projects, ConversationRepository conversations) {
        this.indexations = indexations;
        this.turns = turns;
        this.generations = generations;
        this.projects = projects;
        this.conversations = conversations;
    }

    public List<Item> items() {
        List<Item> items = new ArrayList<>();
        indexations.running().forEach(s -> items.add(new Item(WorkKind.INDEXATION, projectName(s.projectId()), null)));
        for (TurnEvents.OpenTurn turn : turns.openTurns()) {
            Optional<Conversation> conversation = Optional.ofNullable(turn.conversationId()).flatMap(conversations::find);
            WorkKind kind = turn.kind() == TurnEvents.Kind.REVISION ? WorkKind.REVISION : WorkKind.AGENT_REPLY;
            items.add(new Item(kind, conversation.map(c -> projectName(c.projectId())).orElse(null),
                    conversation.map(Conversation::name).orElse(null)));
        }
        generations.current().forEach(e -> items.add(new Item(e.kind(), projectName(e.projectId()), null)));
        return items;
    }

    /** Interrompt les indexations et les tours ; rend le nombre de travaux interrompus. */
    public int cancelAll() {
        int interrupted = indexations.running().size() + turns.openTurns().size();
        indexations.cancelAll();
        turns.cancelAll();
        return interrupted;
    }

    /** Vrai tant qu'un travail interruptible n'a pas fini (les générations ne s'interrompent pas). */
    public boolean interruptibleRunning() {
        return !indexations.running().isEmpty() || !turns.openTurns().isEmpty();
    }

    private String projectName(UUID projectId) {
        return projectId == null ? null : projects.find(projectId).map(p -> p.name().value()).orElse(null);
    }
}
