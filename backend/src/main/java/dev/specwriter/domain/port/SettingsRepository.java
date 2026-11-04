package dev.specwriter.domain.port;

import dev.specwriter.domain.settings.CopilotSettings;
import dev.specwriter.domain.settings.EmbeddingSettings;
import dev.specwriter.domain.settings.JiraSettings;
import java.util.Optional;

/** Réglages des services ; aucun secret n'y est stocké (FR-033a). */
public interface SettingsRepository {

    CopilotSettings copilot();

    void saveCopilot(CopilotSettings settings);

    Optional<JiraSettings> jira();

    void saveJira(JiraSettings settings);

    void deleteJira();

    EmbeddingSettings embedding();

    void saveEmbedding(EmbeddingSettings settings);
}
