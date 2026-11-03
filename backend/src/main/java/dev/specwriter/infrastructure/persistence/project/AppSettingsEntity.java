package dev.specwriter.infrastructure.persistence.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ligne unique des réglages de l'application (V7) : le projet courant. */
@Entity
@Table(name = "app_settings")
public class AppSettingsEntity {
    @Id
    public Integer id = 1;

    @Column(name = "current_project_id")
    public String currentProjectId;
}
