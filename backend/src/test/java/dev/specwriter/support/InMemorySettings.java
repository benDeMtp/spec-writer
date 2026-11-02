package dev.specwriter.support;

import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.settings.CopilotSettings;
import dev.specwriter.domain.settings.JiraSettings;
import java.util.Optional;

public class InMemorySettings implements SettingsRepository {
    private CopilotSettings copilot = CopilotSettings.initial();
    private JiraSettings jira;

    @Override
    public CopilotSettings copilot() {
        return copilot;
    }

    @Override
    public void saveCopilot(CopilotSettings settings) {
        copilot = settings;
    }

    @Override
    public Optional<JiraSettings> jira() {
        return Optional.ofNullable(jira);
    }

    @Override
    public void saveJira(JiraSettings settings) {
        jira = settings;
    }

    @Override
    public void deleteJira() {
        jira = null;
    }
}
