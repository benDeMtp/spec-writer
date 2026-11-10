package dev.specwriter.domain.port;

import dev.specwriter.domain.conversation.CitedSource;
import dev.specwriter.domain.conversation.Conversation;
import dev.specwriter.domain.conversation.ConversationMemory;
import dev.specwriter.domain.conversation.ConversationMessage;
import dev.specwriter.domain.conversation.SearchStep;
import dev.specwriter.domain.conversation.Thinking;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Conversations gardées localement, par projet (010, research.md R6). */
public interface ConversationRepository {

    /** Conversations du projet, la plus récente d'abord. */
    List<Conversation> list(UUID projectId);

    Optional<Conversation> find(UUID id);

    /** Ajoute ou met à jour (nom, dernière activité). */
    void save(Conversation conversation);

    /** Supprime la conversation, ses messages, étapes, sources et documents joints. */
    void delete(UUID id);

    /** Messages dans l'ordre. */
    List<ConversationMessage> messages(UUID conversationId);

    /** Ajoute ou met à jour (texte, état). */
    void saveMessage(ConversationMessage message);

    void saveStep(UUID messageId, SearchStep step);

    List<SearchStep> steps(UUID messageId);

    void saveSources(UUID messageId, List<CitedSource> sources);

    /** Avant un nouvel essai : retire les étapes, les sources et la réflexion de la réponse. */
    void clearTurn(UUID messageId);

    List<CitedSource> sources(UUID messageId);

    /** Remplace la réflexion de l'agent et la durée du tour. */
    void saveThinking(UUID messageId, Thinking thinking);

    Optional<Thinking> thinking(UUID messageId);

    void link(UUID conversationId, UUID sessionId);

    /** Spécifications démarrées depuis la conversation. */
    List<UUID> sessions(UUID conversationId);

    /** Conversation dont vient une spécification. */
    Optional<UUID> conversationOf(UUID sessionId);

    /** Au démarrage : un message resté {@code RUNNING} devient {@code INCOMPLETE} ; renvoie leur nombre. */
    int interruptRunning();

    /** Mémoire de la conversation : le résumé des échanges anciens, s'il y en a un. */
    Optional<ConversationMemory> memory(UUID conversationId);

    /** Remplace la mémoire de la conversation. */
    void saveMemory(UUID conversationId, ConversationMemory memory);
}
