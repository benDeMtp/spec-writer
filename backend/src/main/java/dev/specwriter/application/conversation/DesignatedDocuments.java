package dev.specwriter.application.conversation;

import dev.specwriter.application.conversation.ConversationSearch.Found;
import dev.specwriter.application.knowledge.SelectKnowledge;
import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.conversation.SourceKind;
import dev.specwriter.domain.knowledge.KnowledgeBudget;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.ProjectKnowledge;
import dev.specwriter.domain.port.ConversationAttachmentRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Documents que l'utilisateur a désignés (010, US3) : mentions @ de la question puis des messages
 * précédents, et documents joints à la conversation, selon les règles 007 et 009 (ordre, 4 au plus,
 * le premier coupé à une section s'il le faut). Ils passent en tête des sources de l'agent.
 */
public class DesignatedDocuments {

    /** Aucun document désigné : pour les tests de la boucle qui n'en ont pas besoin. */
    public static final DesignatedDocuments NONE = new DesignatedDocuments(null, null);

    private final SelectKnowledge select;
    private final ConversationAttachmentRepository attachments;

    public DesignatedDocuments(SelectKnowledge select, ConversationAttachmentRepository attachments) {
        this.select = select;
        this.attachments = attachments;
    }

    /** {@code earlier} : messages précédents de l'utilisateur, du plus ancien au plus récent. */
    public List<Found> read(UUID projectId, UUID conversationId, String question, List<String> earlier,
            KnowledgeBudget budget) {
        return read(projectId, conversationId, question, earlier, budget, List.of());
    }

    /** Avec les documents joints à la spécification discutée (011), avant ceux de la conversation. */
    public List<Found> read(UUID projectId, UUID conversationId, String question, List<String> earlier,
            KnowledgeBudget budget, List<Attachment> specificationAttachments) {
        if (select == null) {
            return List.of();
        }
        List<Attachment> joined = new ArrayList<>(specificationAttachments);
        if (attachments != null) {
            joined.addAll(attachments.list(conversationId));
        }
        return select.mentioned(projectId, question, earlier, null, joined, budget, budget.conversationSources() / 2)
                .stream().map(DesignatedDocuments::found).toList();
    }

    private static Found found(ProjectKnowledge.Entry e) {
        SourceKind kind = e.ref().startsWith(Attachment.REF_PREFIX) ? SourceKind.ATTACHMENT
                : e.kind() == KnowledgeKind.ADR ? SourceKind.ADR : SourceKind.DOC_SECTION;
        return new Found(kind, e.ref(), e.title(), e.text());
    }
}
