package fr.kawamind.application.agent;

import fr.kawamind.application.knowledge.KnowledgeQueries;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Outil MCP {@code search_knowledge} (FR-025, research.md R26) : 10 éléments au plus de l'index
 * local, extraits de 600 caractères. Aucune synchronisation, aucun appel à Jira ni à Copilot ; la
 * requête n'est pas consignée.
 */
public class SearchKnowledgeForAgent {

    public static final int MAX_HITS = 10;

    private final KnowledgeQueries queries;

    public SearchKnowledgeForAgent(KnowledgeQueries queries) {
        this.queries = queries;
    }

    public List<KnowledgeQueries.Hit> run(UUID projectId, String query, Collection<KnowledgeKind> kinds) {
        try {
            return queries.search(projectId, query, kinds).stream().limit(MAX_HITS).toList();
        } catch (DomainException e) {
            if (e.code() == ErrorCode.PROJECT_NOT_FOUND) {
                throw new DomainException(ErrorCode.NOT_FOUND);
            }
            throw e;
        }
    }
}
