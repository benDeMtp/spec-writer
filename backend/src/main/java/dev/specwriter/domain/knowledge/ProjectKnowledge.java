package dev.specwriter.domain.knowledge;

import dev.specwriter.domain.knowledge.ArchitectureModel.DeploymentFact;
import dev.specwriter.domain.knowledge.ArchitectureModel.Exchange;
import dev.specwriter.domain.knowledge.KnowledgeSelection.ImpactedModule;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Connaissance du projet transmise aux opérations de l'IA pour une session (research.md R24) : la
 * fiche générale, qui l'emporte sur tout, les éléments retenus avec leur texte borné, et les faits
 * d'architecture calculés. Vide, le message est celui de 001.
 */
public record ProjectKnowledge(String generalSheet, Instant sheetUpdatedAt, List<Entry> entries,
        List<ImpactedModule> modules, List<Exchange> exchanges, List<DeploymentFact> deploymentFacts,
        Subtasks subtasks, int budget) {

    public static final ProjectKnowledge EMPTY = new ProjectKnowledge("", null, List.of(), List.of(), List.of(),
            List.of());

    /**
     * Taille maximale de la fiche générale, et du tout transmis avec un modèle de capacité inconnue
     * (35 000 caractères, {@link KnowledgeBudget#STANDARD}).
     */
    public static final int MAX_SHEET = 20_000;
    public static final int MAX_TOTAL = 35_000;

    /**
     * Élément retenu : vocabulaire, vue d'ensemble, ADR ou ticket, avec son texte transmis ; pour un
     * ticket lié explicitement à la story de la session, la phrase du lien (research.md R29).
     */
    public record Entry(KnowledgeKind kind, KnowledgeRole role, String ref, int chunk, String title, String status,
            boolean closed, String text, String contentHash, String link, boolean designated) {

        public Entry {
            text = text == null ? "" : text;
        }

        public Entry(KnowledgeKind kind, KnowledgeRole role, String ref, int chunk, String title, String status,
                boolean closed, String text, String contentHash, String link) {
            this(kind, role, ref, chunk, title, status, closed, text, contentHash, link, false);
        }

        public Entry(KnowledgeKind kind, KnowledgeRole role, String ref, int chunk, String title, String status,
                boolean closed, String text, String contentHash) {
            this(kind, role, ref, chunk, title, status, closed, text, contentHash, null, false);
        }

        public Entry withLink(String newLink) {
            return new Entry(kind, role, ref, chunk, title, status, closed, text, contentHash, newLink, designated);
        }

        /** Élément désigné par l'utilisateur avec @ (007) : transmis d'office, marqué pour l'IA. */
        public Entry withDesignated() {
            return new Entry(kind, role, ref, chunk, title, status, closed, text, contentHash, link, true);
        }
    }

    /** Sous-tâche de la story de la session : titre seul (research.md R29). */
    public record Subtask(String ref, String status, boolean closed, String title) {
    }

    /** Sous-tâches de la story liée à la session ; {@link #NONE} sans story ou sans sous-tâche. */
    public record Subtasks(String storyKey, List<Subtask> items) {

        public static final Subtasks NONE = new Subtasks(null, List.of());

        public Subtasks {
            items = items == null ? List.of() : List.copyOf(items);
        }

        public boolean isEmpty() {
            return storyKey == null || items.isEmpty();
        }
    }

    public ProjectKnowledge {
        generalSheet = generalSheet == null ? "" : generalSheet;
        sheetUpdatedAt = generalSheet.isBlank() ? null : sheetUpdatedAt;
        entries = entries == null ? List.of() : List.copyOf(entries);
        modules = modules == null ? List.of() : List.copyOf(modules);
        exchanges = exchanges == null ? List.of() : List.copyOf(exchanges);
        deploymentFacts = deploymentFacts == null ? List.of() : List.copyOf(deploymentFacts);
        subtasks = subtasks == null ? Subtasks.NONE : subtasks;
        budget = budget <= 0 ? MAX_TOTAL : budget;
    }

    public ProjectKnowledge(String generalSheet, Instant sheetUpdatedAt, List<Entry> entries,
            List<ImpactedModule> modules, List<Exchange> exchanges, List<DeploymentFact> deploymentFacts,
            Subtasks subtasks) {
        this(generalSheet, sheetUpdatedAt, entries, modules, exchanges, deploymentFacts, subtasks, MAX_TOTAL);
    }

    public ProjectKnowledge(String generalSheet, Instant sheetUpdatedAt, List<Entry> entries,
            List<ImpactedModule> modules, List<Exchange> exchanges, List<DeploymentFact> deploymentFacts) {
        this(generalSheet, sheetUpdatedAt, entries, modules, exchanges, deploymentFacts, Subtasks.NONE);
    }

    /** Fiche générale seule. */
    public static ProjectKnowledge sheet(String generalSheet, Instant updatedAt) {
        return new ProjectKnowledge(generalSheet, updatedAt, List.of(), List.of(), List.of(), List.of());
    }

    public boolean isEmpty() {
        return generalSheet.isBlank() && entries.isEmpty() && modules.isEmpty() && exchanges.isEmpty()
                && deploymentFacts.isEmpty() && subtasks.isEmpty();
    }

    /** La même connaissance, avec les faits d'architecture calculés. */
    public ProjectKnowledge withArchitecture(List<ImpactedModule> newModules, List<Exchange> newExchanges,
            List<DeploymentFact> facts) {
        return new ProjectKnowledge(generalSheet, sheetUpdatedAt, entries, newModules, newExchanges, facts, subtasks,
                budget);
    }

    /** La même connaissance, avec les sous-tâches de la story. */
    public ProjectKnowledge withSubtasks(Subtasks newSubtasks) {
        return new ProjectKnowledge(generalSheet, sheetUpdatedAt, entries, modules, exchanges, deploymentFacts,
                newSubtasks, budget);
    }

    /** La même connaissance, sélectionnée avec le budget total donné (008, selon le modèle choisi). */
    public ProjectKnowledge withBudget(int newBudget) {
        return new ProjectKnowledge(generalSheet, sheetUpdatedAt, entries, modules, exchanges, deploymentFacts,
                subtasks, newBudget);
    }

    public boolean hasArchitecture() {
        return !modules.isEmpty() || !exchanges.isEmpty() || !deploymentFacts.isEmpty();
    }

    /** Références citables par une source de la sorte donnée (data-model.md, SourceRef). */
    public Set<String> citable(KnowledgeKind kind) {
        Set<String> refs = new LinkedHashSet<>();
        if (kind == KnowledgeKind.ARCH_ELEMENT) {
            modules.forEach(m -> refs.add(m.name()));
            return refs;
        }
        entries.stream().filter(e -> e.kind() == kind).forEach(e -> refs.add(e.ref()));
        return refs;
    }

    /** Vide sans fiche ; sinon le SHA-256 de la fiche (FR-011). */
    public String sheetHash() {
        return generalSheet.isBlank() ? "" : sha256(generalSheet);
    }

    /** Ce qui est enregistré avec la version : références et empreintes, faits calculés, sans contenu. */
    public KnowledgeSelection selection() {
        if (isEmpty()) {
            return KnowledgeSelection.EMPTY;
        }
        List<KnowledgeSelection.Selected> items = entries.stream()
                .map(e -> new KnowledgeSelection.Selected(e.kind(), e.ref(), e.chunk(), e.contentHash())).toList();
        return new KnowledgeSelection(generalSheet.isBlank() ? null : sheetHash(), items, modules, exchanges,
                deploymentFacts);
    }

    /**
     * Empreinte de la connaissance pour {@code InputFingerprint} : vide sans connaissance (empreinte
     * de 001), sinon le SHA-256 de la fiche, des références et empreintes retenues et des faits ;
     * le budget seulement s'il n'est pas celui d'aujourd'hui (008, research.md R3).
     */
    public String fingerprint() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder("sheet=").append(sheetHash());
        entries.forEach(e -> {
            out.append("\nitem=").append(e.kind()).append('|').append(e.ref()).append('|').append(e.chunk()).append('|')
                    .append(e.contentHash());
            if (e.link() != null) {
                out.append('|').append(e.link());
            }
        });
        modules.forEach(m -> out.append("\nmodule=").append(m.name()).append('|').append(m.scope()));
        exchanges.forEach(x -> out.append("\nexchange=").append(x.source()).append('|').append(x.destination())
                .append('|').append(x.kind()).append('|').append(x.via()));
        deploymentFacts.forEach(f -> out.append("\nfact=").append(f.kind()).append('|').append(f.element()).append('|')
                .append(f.other()));
        if (!subtasks.isEmpty()) {
            out.append("\nstory=").append(subtasks.storyKey());
            subtasks.items().forEach(t -> out.append("\nsubtask=").append(t.ref()).append('|').append(t.status())
                    .append('|').append(t.title()));
        }
        if (budget != MAX_TOTAL) {
            out.append("\nbudget=").append(budget);
        }
        return sha256(out.toString());
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
