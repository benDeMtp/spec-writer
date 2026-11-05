package dev.specwriter.application.knowledge;

import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.jira.TicketLink;
import dev.specwriter.domain.port.LinkedTicketRepository;
import dev.specwriter.domain.port.TicketLinkRepository;
import dev.specwriter.domain.session.Session;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Famille de la story liée à une session (FR-045, research.md R29) : la story, son parent et ses
 * sous-tâches, qui ne sont jamais des tickets proches ni des travaux concurrents, et les liens
 * explicites de la story, rendus comme une phrase Jira (« FACT-20 blocks FACT-30 »). Si la story
 * est elle-même une sous-tâche, son parent est de la famille, pas les autres sous-tâches du parent.
 */
public class StoryFamily {

    /** Famille d'une story ; {@link #NONE} pour une session sans ticket lié. */
    public record Family(String storyKey, String parentKey, List<String> subtasks, Map<String, String> links) {

        public static final Family NONE = new Family(null, null, List.of(), Map.of());

        public Family {
            subtasks = List.copyOf(subtasks);
            links = Map.copyOf(links);
        }

        public boolean isEmpty() {
            return storyKey == null;
        }

        /** Vrai pour la story, son parent et ses sous-tâches. */
        public boolean contains(String key) {
            return key != null && (key.equals(storyKey) || key.equals(parentKey) || subtasks.contains(key));
        }

        /** Lien explicite d'un ticket avec la story, ou {@code null}. */
        public String link(String key) {
            return links.get(key);
        }
    }

    private final LinkedTicketRepository tickets;
    private final TicketLinkRepository links;

    public StoryFamily(LinkedTicketRepository tickets, TicketLinkRepository links) {
        this.tickets = tickets;
        this.links = links;
    }

    /** Sans liens connus : aucune famille (jeux d'essai qui ne décrivent pas de sous-tâches). */
    public static StoryFamily none() {
        return new StoryFamily(null, null);
    }

    public Family of(Session session) {
        if (tickets == null || session.projectId() == null) {
            return Family.NONE;
        }
        return tickets.findBySession(session.id()).map(LinkedJiraTicket::issueKey)
                .map(key -> of(session.projectId(), key)).orElse(Family.NONE);
    }

    public Family of(UUID projectId, String story) {
        List<TicketLink> own = links.of(projectId, story);
        List<TicketLinkRepository.Inbound> inbound = links.pointingTo(projectId, story);
        String parent = parentOf(own, inbound).orElse(null);
        Set<String> subtasks = new LinkedHashSet<>();
        own.stream().filter(l -> l.kind() == TicketLink.Kind.SUBTASK).forEach(l -> subtasks.add(l.targetKey()));
        inbound.stream().filter(i -> i.link().kind() == TicketLink.Kind.PARENT).forEach(i -> subtasks.add(i.sourceKey()));
        subtasks.remove(story);
        Map<String, String> explicit = new LinkedHashMap<>();
        own.stream().filter(l -> l.kind() == TicketLink.Kind.LINK)
                .forEach(l -> explicit.putIfAbsent(l.targetKey(), sentence(story, l, l.targetKey())));
        inbound.stream().filter(i -> i.link().kind() == TicketLink.Kind.LINK)
                .forEach(i -> explicit.putIfAbsent(i.sourceKey(), sentence(i.sourceKey(), i.link(), story)));
        Family family = new Family(story, parent, List.copyOf(subtasks), Map.of());
        explicit.keySet().removeIf(family::contains);
        return new Family(story, parent, List.copyOf(subtasks), explicit);
    }

    /** Parent d'un ticket indexé, s'il en a un. */
    public Optional<String> parentOf(UUID projectId, String key) {
        if (links == null) {
            return Optional.empty();
        }
        return parentOf(links.of(projectId, key), links.pointingTo(projectId, key));
    }

    private static Optional<String> parentOf(List<TicketLink> own, List<TicketLinkRepository.Inbound> inbound) {
        return own.stream().filter(l -> l.kind() == TicketLink.Kind.PARENT).map(TicketLink::targetKey).findFirst()
                .or(() -> inbound.stream().filter(i -> i.link().kind() == TicketLink.Kind.SUBTASK)
                        .map(TicketLinkRepository.Inbound::sourceKey).findFirst());
    }

    /**
     * « FACT-20 blocks FACT-30 » : le libellé de Jira est propre au sens du lien (« blocks », « is
     * blocked by »), il se lit donc toujours du ticket qui porte le lien vers l'autre.
     */
    private static String sentence(String holder, TicketLink link, String other) {
        String label = link.label() == null || link.label().isBlank() ? link.linkType() : link.label();
        return holder + " " + label + " " + other;
    }
}
