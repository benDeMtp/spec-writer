package dev.specwriter.domain.specification;

import dev.specwriter.domain.attachment.Attachment;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import java.util.Set;
import java.util.UUID;

/**
 * Source d'un use case : extrait du besoin, réponse, hypothèse, extrait de la fiche générale, ou
 * élément de la connaissance du projet cité par sa référence (FR-010 de 002, research.md R24).
 * {@code ref} : clé du ticket, URL de la page ou de l'ADR, nom canonique de l'élément
 * d'architecture ; {@code null} pour les autres sortes. Le champ {@code sheet} des versions
 * antérieures à la fiche générale est ignoré à la lecture.
 */
public record SourceRef(Kind kind, UUID questionId, String excerpt, String ref) {

    public SourceRef(Kind kind, UUID questionId, String excerpt) {
        this(kind, questionId, excerpt, null);
    }

    /** Élément de la connaissance du projet, cité par sa référence. */
    public static SourceRef knowledge(Kind kind, String ref, String excerpt) {
        return new SourceRef(kind, null, excerpt, ref);
    }

    public SourceRef withRef(String newRef) {
        return new SourceRef(kind, questionId, excerpt, newRef);
    }

    /** Document joint à la session (009) : cité par son nom, jamais par un extrait (research.md R6). */
    public boolean attachment() {
        return kind == Kind.DOC_PAGE && ref != null && ref.startsWith(Attachment.REF_PREFIX);
    }

    /** Sans extrait : le texte d'un document joint ne sort pas de l'outil. */
    public SourceRef withoutExcerpt() {
        return new SourceRef(kind, questionId, null, ref);
    }

    public enum Kind {
        NEED_EXCERPT,
        ANSWER,
        ASSUMPTION,
        PROJECT_CONTEXT,
        JIRA_ISSUE,
        DOC_PAGE,
        ARCH_ELEMENT;

        /** Sortes d'éléments de la connaissance qu'une source de cette sorte peut citer ; vide sinon. */
        public Set<KnowledgeKind> knowledgeKinds() {
            return switch (this) {
                case JIRA_ISSUE -> Set.of(KnowledgeKind.TICKET);
                case DOC_PAGE -> Set.of(KnowledgeKind.DOC_SECTION, KnowledgeKind.ADR);
                case ARCH_ELEMENT -> Set.of(KnowledgeKind.ARCH_ELEMENT);
                default -> Set.of();
            };
        }
    }
}
