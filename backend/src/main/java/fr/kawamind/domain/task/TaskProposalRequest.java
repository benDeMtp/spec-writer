package fr.kawamind.domain.task;

import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.sources.Qualification;
import java.util.List;
import java.util.Objects;

/**
 * Données transmises au modèle pour proposer des tâches (015, R8) : la spécification (titre, résumé,
 * cas d'usage), les modules du périmètre s'il y en a, les tâches déjà suivies et les bornes. Ni
 * documents ni connaissance du projet : le découpage porte sur la spécification.
 */
public record TaskProposalRequest(Language language, String title, String summary, List<UseCaseRef> useCases,
        List<ModuleRef> modules, List<String> existingTitles, TaskProposal.Bounds bounds) {

    /** Cas d'usage cité par son code. */
    public record UseCaseRef(String code, String title, String goal) {
    }

    /** Module du périmètre (013) : nom, qualification, justification. */
    public record ModuleRef(String name, Qualification qualification, String justification) {
    }

    public TaskProposalRequest {
        Objects.requireNonNull(language);
        Objects.requireNonNull(bounds);
        useCases = List.copyOf(useCases);
        modules = List.copyOf(modules);
        existingTitles = List.copyOf(existingTitles);
    }

    public TaskProposal.Basing basing() {
        return modules.isEmpty() ? TaskProposal.Basing.USE_CASES : TaskProposal.Basing.MODULES;
    }
}
