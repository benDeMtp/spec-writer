package dev.specwriter.infrastructure.persistence.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Ligne unique des réglages de l'application (V7, V10) : le projet courant, le modèle d'embedding et le navigateur (V25). */
@Entity
@Table(name = "app_settings")
public class AppSettingsEntity {
    @Id
    public Integer id = 1;

    @Column(name = "current_project_id")
    public String currentProjectId;

    @Column(name = "embedding_model_folder")
    public String embeddingModelFolder;

    @Column(name = "embedding_pooling")
    public String embeddingPooling;

    @Column(name = "embedding_query_prefix")
    public String embeddingQueryPrefix;

    @Column(name = "embedding_passage_prefix")
    public String embeddingPassagePrefix;

    @Column(name = "browser")
    public String browser;
}
