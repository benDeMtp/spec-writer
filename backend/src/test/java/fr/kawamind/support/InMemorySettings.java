package fr.kawamind.support;

import fr.kawamind.domain.port.SettingsRepository;
import fr.kawamind.domain.settings.CopilotSettings;
import fr.kawamind.domain.settings.EmbeddingSettings;
import fr.kawamind.domain.settings.JiraSettings;
import java.util.Optional;

public class InMemorySettings implements SettingsRepository {
    private CopilotSettings copilot = CopilotSettings.initial();
    private JiraSettings jira;
    private EmbeddingSettings embedding = EmbeddingSettings.DEFAULT;
    private String browser;

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

    @Override
    public EmbeddingSettings embedding() {
        return embedding;
    }

    @Override
    public void saveEmbedding(EmbeddingSettings settings) {
        embedding = settings;
    }

    @Override
    public String browser() {
        return browser;
    }

    @Override
    public void saveBrowser(String browserId) {
        browser = browserId;
    }
}
