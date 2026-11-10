package dev.specwriter.support;

import dev.specwriter.domain.conversation.SpecificationDiscussion;
import dev.specwriter.domain.port.SpecificationDiscussionRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Discussions des spécifications en mémoire (011). */
public class InMemorySpecificationDiscussions implements SpecificationDiscussionRepository {

    private final Map<UUID, SpecificationDiscussion> byConversation = new LinkedHashMap<>();

    @Override
    public synchronized Optional<SpecificationDiscussion> current(UUID sessionId) {
        return byConversation.values().stream().filter(d -> sessionId.equals(d.sessionId()) && d.current())
                .findFirst();
    }

    @Override
    public synchronized Optional<SpecificationDiscussion> ofConversation(UUID conversationId) {
        return Optional.ofNullable(byConversation.get(conversationId));
    }

    @Override
    public synchronized Map<UUID, SpecificationDiscussion> ofConversations(Collection<UUID> conversationIds) {
        Map<UUID, SpecificationDiscussion> out = new LinkedHashMap<>();
        conversationIds.forEach(id -> ofConversation(id).ifPresent(d -> out.put(id, d)));
        return out;
    }

    @Override
    public synchronized List<SpecificationDiscussion> bySession(UUID sessionId) {
        return byConversation.values().stream().filter(d -> sessionId.equals(d.sessionId())).toList();
    }

    @Override
    public synchronized void save(SpecificationDiscussion discussion) {
        if (discussion.current() && current(discussion.sessionId())
                .filter(d -> !d.conversationId().equals(discussion.conversationId())).isPresent()) {
            throw new IllegalStateException("Une seule discussion suivie par spécification");
        }
        byConversation.put(discussion.conversationId(), discussion);
    }

    @Override
    public synchronized void detachAll(UUID sessionId) {
        bySession(sessionId).forEach(d -> byConversation.put(d.conversationId(), d.detached()));
    }

    @Override
    public synchronized void renameAll(UUID sessionId, String name) {
        bySession(sessionId).forEach(d -> byConversation.put(d.conversationId(), new SpecificationDiscussion(
                d.conversationId(), d.sessionId(), name, d.current(), d.createdAt())));
    }

    /** Une conversation supprimée emporte son rattachement (cascade). */
    public synchronized void conversationDeleted(UUID conversationId) {
        byConversation.remove(conversationId);
    }
}
