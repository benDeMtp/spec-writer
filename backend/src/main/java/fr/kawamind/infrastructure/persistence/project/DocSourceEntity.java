package fr.kawamind.infrastructure.persistence.project;

import fr.kawamind.infrastructure.persistence.InstantTextConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "project_doc_source")
public class DocSourceEntity {
    @Id
    public String id;

    @Column(name = "project_id", nullable = false)
    public String projectId;

    @Column(nullable = false)
    public String kind;

    @Column(nullable = false)
    public String location;

    @Convert(converter = InstantTextConverter.class)
    @Column(name = "last_read_at")
    public Instant lastReadAt;

    /** JSON {@code {pagesRead, pagesInError, limitReached}}, sans contenu. */
    @Column(name = "last_result")
    public String lastResult;

    /** Tableau JSON des motifs d'exclusion d'un dossier de documents (006), {@code null} sinon. */
    public String exclusions;

    /** Niveau propre d'une racine Confluence (016), {@code null} sinon ou sans niveau propre. */
    public String reliability;

    /** Identifiant de la page racine Confluence (016). */
    @Column(name = "page_id")
    public String pageId;

    /** Titre de la page racine Confluence (016). */
    public String title;
}
