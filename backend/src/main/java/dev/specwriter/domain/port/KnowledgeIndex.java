package dev.specwriter.domain.port;

import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.Mentionable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Index local de la connaissance d'un projet (research.md R18, R20) : éléments, vecteurs et modèle
 * d'architecture. Le contenu ne quitte jamais le dossier de données et n'est jamais journalisé.
 */
public interface KnowledgeIndex {

    /** Identité d'un élément dans un projet. */
    record Key(KnowledgeKind kind, String ref, int chunk) {

        public static Key of(KnowledgeItem item) {
            return new Key(item.kind(), item.ref(), item.chunk());
        }
    }

    /** Élément retrouvé et son score de fusion (plus grand = plus pertinent). */
    record Hit(KnowledgeItem item, double score) {
    }

    /** Empreintes des éléments indexés d'une sorte, pour ne réécrire que ce qui change. */
    Map<Key, String> hashes(UUID projectId, KnowledgeKind kind);

    /**
     * Écrit l'élément s'il est nouveau ou si son empreinte change ; {@code vector} nul garde
     * l'élément sans vecteur (modèle absent).
     */
    void save(KnowledgeItem item, float[] vector, String embeddingModel);

    /** Supprime les éléments de la sorte qui ne sont plus dans {@code keep} ; renvoie leur nombre. */
    int retain(UUID projectId, KnowledgeKind kind, Set<Key> keep);

    /**
     * Supprime les éléments de toute sorte dont la référence commence par {@code refPrefix} et qui ne
     * sont pas dans {@code keep} (éléments d'une seule source, 006) ; renvoie leur nombre.
     */
    int retainUnder(UUID projectId, String refPrefix, Set<Key> keep);

    /**
     * Remplace les éléments d'une source de documentation lue en entier : ceux de {@code keep} lui
     * sont rattachés, ceux qu'elle avait et qui n'y sont plus sont supprimés (page retirée,
     * renommée, ADR renuméroté). {@code keep} vide purge la source ; renvoie le nombre supprimé.
     */
    int replaceSource(UUID projectId, UUID sourceId, Set<Key> keep);

    /** Supprime un élément (ticket supprimé dans Jira, par exemple). */
    void delete(UUID projectId, Key key);

    Optional<KnowledgeItem> find(UUID projectId, Key key);

    /** Éléments sans vecteur, ou vectorisés par un autre modèle. */
    List<KnowledgeItem> withoutVector(UUID projectId, String embeddingModel, int limit);

    /** Revectorise un élément déjà indexé, sans changer son contenu. */
    void saveVector(UUID projectId, Key key, float[] vector, String embeddingModel);

    /**
     * Recherche hybride : plein texte et, si {@code queryVector} est donné, vecteurs ; fusion des
     * rangs (research.md R18). Seuls les vecteurs du même modèle sont comparés. Les termes ne sont
     * jamais interprétés comme une syntaxe de requête ; un seul suffit pour retenir un élément.
     */
    List<Hit> search(UUID projectId, String terms, float[] queryVector, String embeddingModel,
            Collection<KnowledgeKind> kinds, int limit);

    /** Tous les éléments d'une sorte (les ADR, pour retrouver le successeur d'un ADR remplacé). */
    List<KnowledgeItem> all(UUID projectId, KnowledgeKind kind);

    /** Éléments d'un rôle donné (vocabulaire, vue d'ensemble), toujours transmis (FR-042). */
    List<KnowledgeItem> withRole(UUID projectId);

    /**
     * Documents mentionnables avec @ (007, research.md R3) : sections de documentation sans rôle et
     * ADR, un élément par document, sans les corps.
     */
    List<Mentionable> mentionables(UUID projectId);

    /**
     * Sections d'un document (sa référence, puis {@code ref#…}) dans l'ordre où elles ont été
     * indexées, qui est celui du document (007 : page Antora recomposée).
     */
    List<KnowledgeItem> documentParts(UUID projectId, String documentRef);

    /** Remplace les relations et déploiements du modèle d'architecture du projet. */
    void replaceArchitecture(UUID projectId, ArchitectureModel model);

    ArchitectureModel architecture(UUID projectId);

    long count(UUID projectId, KnowledgeKind kind);

    /** Clés des tickets indexés fermés (catégorie {@code done}), gardés sans relecture (FR-002 de 003). */
    Set<String> closedTickets(UUID projectId);
}
