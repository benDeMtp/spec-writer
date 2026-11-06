package dev.specwriter.application.knowledge;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.project.DocSource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sources d'une indexation (005, research.md R2) : toutes ({@link #ALL}), ou celles que l'utilisateur
 * a choisies, désignées par {@code JIRA}, {@code ARCHITECTURE} et l'identifiant de chaque source de
 * documentation.
 *
 * @param docSources sources de documentation choisies ; {@code null} pour toutes
 */
public record IndexationScope(boolean jira, Set<UUID> docSources, boolean architecture) {

    public static final String JIRA = "JIRA";
    public static final String ARCHITECTURE = "ARCHITECTURE";
    public static final IndexationScope ALL = new IndexationScope(true, null, true);

    public IndexationScope {
        docSources = docSources == null ? null : Set.copyOf(docSources);
    }

    /** Sources choisies parmi celles déclarées pour le projet ; refus d'un choix vide ou inconnu. */
    public static IndexationScope of(List<String> ids, List<DocSource> declared) {
        if (ids == null || ids.isEmpty()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Choisir au moins une source à réindexer.");
        }
        Map<String, UUID> known = declared.stream()
                .collect(Collectors.toMap(s -> s.id().toString(), DocSource::id, (a, b) -> a));
        boolean jira = false;
        boolean architecture = false;
        Set<UUID> docs = new LinkedHashSet<>();
        for (String id : ids) {
            if (JIRA.equals(id)) {
                jira = true;
            } else if (ARCHITECTURE.equals(id)) {
                architecture = true;
            } else if (id != null && known.containsKey(id)) {
                docs.add(known.get(id));
            } else {
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "Source inconnue pour ce projet.");
            }
        }
        return new IndexationScope(jira, docs, architecture);
    }

    /** Toutes les sources : la documentation peut retirer ce qu'elle n'a pas lu. */
    public boolean all() {
        return this.equals(ALL);
    }

    public boolean includes(DocSource source) {
        return docSources == null || docSources.contains(source.id());
    }
}
