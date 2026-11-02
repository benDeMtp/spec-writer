package dev.specwriter.infrastructure.persistence.settings;

import dev.specwriter.domain.port.SettingsRepository;
import dev.specwriter.domain.settings.CopilotSettings;
import dev.specwriter.domain.settings.JiraSettings;
import dev.specwriter.domain.settings.JiraTestResult;
import dev.specwriter.domain.settings.SecretPersistence;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Optional;

@ApplicationScoped
public class SettingsJpaRepository implements SettingsRepository {

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public CopilotSettings copilot() {
        CopilotSettingsEntity e = em.find(CopilotSettingsEntity.class, 1);
        if (e == null) {
            return CopilotSettings.initial();
        }
        return new CopilotSettings(e.connectedLogin, CopilotSettings.SubscriptionState.valueOf(e.subscriptionState),
                CopilotSettings.AuthMode.valueOf(e.authMode), e.model, e.availableModels, e.modelPreference,
                e.tokenExpiresAt, SecretPersistence.valueOf(e.secretPersistence));
    }

    @Override
    @Transactional
    public void saveCopilot(CopilotSettings s) {
        CopilotSettingsEntity e = Optional.ofNullable(em.find(CopilotSettingsEntity.class, 1)).orElseGet(CopilotSettingsEntity::new);
        e.connectedLogin = s.connectedLogin();
        e.subscriptionState = s.subscriptionState().name();
        e.authMode = s.authMode().name();
        e.model = s.model();
        e.availableModels = s.availableModels();
        e.modelPreference = s.modelPreference();
        e.tokenExpiresAt = s.tokenExpiresAt();
        e.secretPersistence = s.secretPersistence().name();
        em.merge(e);
    }

    @Override
    @Transactional
    public Optional<JiraSettings> jira() {
        JiraSettingsEntity e = em.find(JiraSettingsEntity.class, 1);
        if (e == null) {
            return Optional.empty();
        }
        JiraSettings.LastTest last = e.lastTestResult == null ? null
                : new JiraSettings.LastTest(e.lastTestAt, JiraTestResult.valueOf(e.lastTestResult));
        return Optional.of(new JiraSettings(e.enabled, e.serverUrl, e.defaultProjectKey, e.caCertificatePath,
                e.serverVersion, last, SecretPersistence.valueOf(e.secretPersistence), e.insecureHttp));
    }

    @Override
    @Transactional
    public void saveJira(JiraSettings s) {
        JiraSettingsEntity e = Optional.ofNullable(em.find(JiraSettingsEntity.class, 1)).orElseGet(JiraSettingsEntity::new);
        e.enabled = s.enabled();
        e.serverUrl = s.serverUrl();
        e.defaultProjectKey = s.defaultProjectKey();
        e.caCertificatePath = s.caCertificatePath();
        e.serverVersion = s.serverVersion();
        e.lastTestAt = s.lastTest() == null ? null : s.lastTest().at();
        e.lastTestResult = s.lastTest() == null ? null : s.lastTest().result().name();
        e.secretPersistence = s.secretPersistence().name();
        e.insecureHttp = s.insecureHttp();
        em.merge(e);
    }

    @Override
    @Transactional
    public void deleteJira() {
        JiraSettingsEntity e = em.find(JiraSettingsEntity.class, 1);
        if (e != null) {
            em.remove(e);
        }
    }
}
