package fr.kawamind.domain.jira;

import java.util.List;

/**
 * Story liée lue pour ses tâches (015) : son statut, si elle est elle-même une sous-tâche (elle ne
 * peut alors pas en porter) et ses sous-tâches, dans l'ordre de Jira.
 */
public record ParentIssue(String key, String status, String statusCategory, boolean subtask, List<Subtask> subtasks) {

    public ParentIssue {
        subtasks = subtasks == null ? List.of() : List.copyOf(subtasks);
    }
}
