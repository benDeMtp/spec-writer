package fr.kawamind.domain.port;

import fr.kawamind.domain.jira.JiraLabel;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Label de reconnaissance de chaque projet (024, research R1) ; vide sans réglage. */
public interface JiraLabelRepository {

    Optional<JiraLabel> find(UUID projectId);

    void save(UUID projectId, JiraLabel label, Instant at);

    void delete(UUID projectId);
}
