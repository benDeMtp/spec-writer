package dev.specwriter.infrastructure.persistence.settings;

import dev.specwriter.domain.settings.ModelCapacity;
import dev.specwriter.infrastructure.persistence.InstantTextConverter;
import dev.specwriter.infrastructure.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "copilot_settings")
public class CopilotSettingsEntity {
    @Id
    public Integer id = 1;

    @Column(name = "connected_login")
    public String connectedLogin;

    @Column(name = "subscription_state", nullable = false)
    public String subscriptionState;

    @Column(name = "auth_mode", nullable = false)
    public String authMode;

    public String model;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "available_models", nullable = false)
    public List<String> availableModels;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "model_preference", nullable = false)
    public List<String> modelPreference;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "token_expires_at")
    public Instant tokenExpiresAt;

    @Column(name = "secret_persistence", nullable = false)
    public String secretPersistence;

    @Convert(converter = ModelCapacitiesJsonConverter.class)
    @Column(name = "model_capacities", nullable = false)
    public List<ModelCapacity> modelCapacities;
}
