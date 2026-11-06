package dev.specwriter.application.knowledge;

import java.util.List;
import java.util.Map;

/**
 * ADR d'un projet pour la page des ADR (feature 004, data-model.md) : une ligne par ADR, le nombre
 * d'ADR par statut, et si une indexation est en cours.
 */
public record AdrList(List<AdrSummary> adrs, Map<String, Integer> byStatus, boolean indexing) {

    /**
     * Une ligne : numéro (nul sans numéro), titre, statut ({@code UNKNOWN} s'il n'a pas été lu),
     * successeur cité et s'il existe, doublon de numéro, transmission à l'IA, adresse de la page.
     */
    public record AdrSummary(Integer number, String title, String status, String supersededBy,
            boolean successorFound, boolean duplicate, boolean sentToAi, String url) {
    }
}
