package dev.specwriter.infrastructure.persistence.settings;

import dev.specwriter.infrastructure.persistence.InstantTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "jira_settings")
public class JiraSettingsEntity {
    @Id
    public Integer id = 1;

    @Column(nullable = false)
    public boolean enabled;

    @Column(name = "server_url", nullable = false)
    public String serverUrl;

    @Column(name = "default_project_key", nullable = false)
    public String defaultProjectKey;

    @Column(name = "ca_certificate_path")
    public String caCertificatePath;

    @Column(name = "server_version")
    public String serverVersion;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "last_test_at")
    public Instant lastTestAt;

    @Column(name = "last_test_result")
    public String lastTestResult;

    @Column(name = "secret_persistence", nullable = false)
    public String secretPersistence;

    @Column(name = "insecure_http", nullable = false)
    public boolean insecureHttp;
}
