package dev.specwriter.application.conversation;

import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.port.KnowledgeIndex;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Exemples de questions de l'accueil (010) : tirés de la connaissance indexée du projet courant, sans
 * appel à l'IA. Un ADR en vigueur (sinon un élément d'architecture), un ticket ouvert et une page de
 * documentation, les plus récents ; rien si le projet n'a encore rien d'indexé.
 */
public class ConversationSuggestions {

    static final int MAX_TITLE = 60;

    private final KnowledgeIndex index;

    public ConversationSuggestions(KnowledgeIndex index) {
        this.index = index;
    }

    public List<String> of(UUID projectId) {
        List<String> out = new ArrayList<>();
        latest(projectId, KnowledgeKind.ADR, a -> AdrStatus.ACCEPTED.name().equals(a.status()))
                .map(a -> "Que décide l’ADR « " + cut(a.title()) + " », et qu’est-ce que cela implique ?")
                .or(() -> latest(projectId, KnowledgeKind.ARCH_ELEMENT, e -> true)
                        .map(e -> "Quel rôle joue « " + cut(e.title()) + " » dans l’architecture du projet ?"))
                .ifPresent(out::add);
        latest(projectId, KnowledgeKind.TICKET, t -> !KnowledgeItem.DONE.equals(t.statusCategory()))
                .map(t -> "Où en est " + t.ref() + " « " + cut(t.title()) + " », et qu’en dit la documentation ?")
                .ifPresent(out::add);
        latest(projectId, KnowledgeKind.DOC_SECTION, d -> true)
                .map(d -> "La documentation « " + cut(page(d.title())) + " » et les tickets sont-ils cohérents ?")
                .ifPresent(out::add);
        return List.copyOf(out);
    }

    private Optional<KnowledgeItem> latest(UUID projectId, KnowledgeKind kind,
            Predicate<KnowledgeItem> wanted) {
        return index.all(projectId, kind).stream()
                .filter(i -> i.title() != null && !i.title().isBlank())
                .filter(wanted)
                .max(Comparator.comparing((KnowledgeItem i) -> i.sourceUpdatedAt() == null ? Instant.EPOCH
                        : i.sourceUpdatedAt()).thenComparing(KnowledgeItem::ref, Comparator.reverseOrder()));
    }

    /** Titre d'une section : « Page > Section » ; la page suffit à l'exemple. */
    private static String page(String title) {
        int at = title.indexOf(" > ");
        return at > 0 ? title.substring(0, at) : title;
    }

    private static String cut(String text) {
        String t = text.strip().replaceAll("\\s+", " ");
        return t.length() <= MAX_TITLE ? t : t.substring(0, MAX_TITLE - 1).strip() + "…";
    }
}
