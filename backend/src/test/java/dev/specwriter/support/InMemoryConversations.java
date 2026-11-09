package dev.specwriter.support;

import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.MessageState;
import dev.specwriter.domain.conversation.SearchStep;
import dev.specwriter.domain.port.ConversationRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryConversations implements ConversationRepository {

    private final Map<UUID, Conversation> conversations = new LinkedHashMap<>();
    private final Map<UUID, ConversationMessage> messages = new LinkedHashMap<>();
    private final Map<UUID, List<SearchStep>> steps = new LinkedHashMap<>();
    private final Map<UUID, List<CitedSource>> sources = new LinkedHashMap<>();
    private final Map<UUID, List<UUID>> links = new LinkedHashMap<>();
    /** Nombre d'enregistrements d'un message (texte partiel enregistré au fil de l'écriture). */
    public int messageSaves;

    @Override
    public synchronized List<Conversation> list(UUID projectId) {
        return conversations.values().stream().filter(c -> c.projectId().equals(projectId))
                .sorted(Comparator.comparing(Conversation::updatedAt).reversed()).toList();
    }

    @Override
    public synchronized Optional<Conversation> find(UUID id) {
        return Optional.ofNullable(conversations.get(id));
    }

    @Override
    public synchronized void save(Conversation c) {
        conversations.put(c.id(), c);
    }

    @Override
    public synchronized void delete(UUID id) {
        conversations.remove(id);
        messages.values().removeIf(m -> m.conversationId().equals(id));
        links.remove(id);
    }

    @Override
    public synchronized List<ConversationMessage> messages(UUID conversationId) {
        return messages.values().stream().filter(m -> m.conversationId().equals(conversationId))
                .sorted(Comparator.comparingInt(ConversationMessage::ordinal)).toList();
    }

    @Override
    public synchronized void saveMessage(ConversationMessage m) {
        messageSaves++;
        messages.put(m.id(), m);
    }

    @Override
    public synchronized void saveStep(UUID messageId, SearchStep step) {
        steps.computeIfAbsent(messageId, k -> new ArrayList<>()).add(step);
    }

    @Override
    public synchronized List<SearchStep> steps(UUID messageId) {
        return List.copyOf(steps.getOrDefault(messageId, List.of()));
    }

    @Override
    public synchronized void saveSources(UUID messageId, List<CitedSource> cited) {
        sources.put(messageId, List.copyOf(cited));
    }

    @Override
    public synchronized void clearTurn(UUID messageId) {
        steps.remove(messageId);
        sources.remove(messageId);
    }

    @Override
    public synchronized List<CitedSource> sources(UUID messageId) {
        return sources.getOrDefault(messageId, List.of());
    }

    @Override
    public synchronized void link(UUID conversationId, UUID sessionId) {
        links.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(sessionId);
    }

    @Override
    public synchronized List<UUID> sessions(UUID conversationId) {
        return List.copyOf(links.getOrDefault(conversationId, List.of()));
    }

    @Override
    public synchronized Optional<UUID> conversationOf(UUID sessionId) {
        return links.entrySet().stream().filter(e -> e.getValue().contains(sessionId)).map(Map.Entry::getKey).findFirst();
    }

    @Override
    public synchronized int interruptRunning() {
        int n = 0;
        for (ConversationMessage m : List.copyOf(messages.values())) {
            if (m.state() == MessageState.RUNNING) {
                messages.put(m.id(), m.finished(MessageState.INCOMPLETE, m.historyTruncated()));
                n++;
            }
        }
        return n;
    }
}
