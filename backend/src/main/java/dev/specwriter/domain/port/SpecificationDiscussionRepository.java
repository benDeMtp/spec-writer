package dev.specwriter.domain.port;

import dev.specwriter.domain.conversation.SpecificationDiscussion;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Discussions des spécifications (011, data-model.md), gardées localement. */
public interface SpecificationDiscussionRepository {

    /** Discussion suivie de la spécification, s'il y en a une. */
    Optional<SpecificationDiscussion> current(UUID sessionId);

    /** Rattachement de la conversation, si c'est une discussion. */
    Optional<SpecificationDiscussion> ofConversation(UUID conversationId);

    /** Rattachements des conversations données, par conversation. */
    Map<UUID, SpecificationDiscussion> ofConversations(Collection<UUID> conversationIds);

    /** Toutes les discussions de la spécification, suivie ou non. */
    List<SpecificationDiscussion> bySession(UUID sessionId);

    /** Ajoute ou met à jour (suivie, rattachement). */
    void save(SpecificationDiscussion discussion);

    /** Spécification supprimée, discussions gardées : elles perdent leur rattachement. */
    void detachAll(UUID sessionId);

    /** La spécification a été renommée. */
    void renameAll(UUID sessionId, String name);
}
