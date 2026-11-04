package dev.specwriter.infrastructure.web;

import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.knowledge.KnowledgeSelection;
import dev.specwriter.domain.specification.SpecificationContent;
import java.util.Map;
import dev.specwriter.application.session.ConcludeQuestionnaire;
import dev.specwriter.application.session.ManageSessions;
import dev.specwriter.application.session.SessionSnapshot;
import dev.specwriter.application.specification.ProposalStore;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.QuestionState;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.scenario.AcceptanceScenario;
import dev.specwriter.domain.session.Implementation;
import dev.specwriter.domain.session.ImplementationStatus;
import dev.specwriter.domain.session.Session;
import dev.specwriter.domain.session.SessionRelation;
import dev.specwriter.domain.session.SessionListing;
import dev.specwriter.domain.specification.Assumption;
import dev.specwriter.domain.specification.Requirement;
import dev.specwriter.domain.specification.SpecificationVersion;
import dev.specwriter.domain.specification.UseCase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Représentations JSON des sessions et versions (contracts/openapi.yaml). */
final class SessionViews {

    record Progress(int answered, int skipped, int total) {
    }

    record QuestionView(UUID id, String theme, String label, String rationale, List<String> suggestions,
            String defaultValue, QuestionState state, String answer) {
    }

    record SessionSummary(UUID id, String name, String state, Instant updatedAt, Integer latestVersion,
            String jiraKey, boolean readable, String implementationStatus) {
    }

    /** Statut d'implémentation (FR-026) ; {@code evolvedFromSessionId} : spécification d'origine d'une évolution. */
    record ImplementationView(String status, Instant implementedAt, UUID evolvedFromSessionId) {

        static ImplementationView of(Implementation i) {
            return new ImplementationView(i.status().name(), i.implementedAt(), i.evolvedFrom());
        }
    }

    record RevisionView(List<String> removed, List<String> added) {
    }

    /** {@code relatesTo} et {@code relatesToLink} : ticket fermé lié par « relates to » et état du lien (FR-035 de 002). */
    record LinkedTicketView(String issueKey, String issueUrl, String syncState, int linkedVersionNumber, String relatesTo,
            String relatesToLink) {
    }

    record SessionDetail(UUID id, UUID projectId, String name, String state, Instant updatedAt, Integer latestVersion,
            String jiraKey, LinkedTicketView linkedTicket, String origin, String need, String language, Progress progress, List<QuestionView> currentBatch,
            List<QuestionView> answeredQuestions, List<String> warnings, RevisionView revision,
            ImplementationView implementation) {
    }

    record VersionSummary(int number, String origin, Instant createdAt, boolean pushedToJira) {
    }

    /**
     * {@code contextUpdatedAt} : date de la fiche générale utilisée, absente sans fiche (FR-013d) ;
     * {@code architecture} : faits calculés par l'outil (FR-039 à FR-041) ; {@code links} : adresse
     * des sources de la connaissance (ticket Jira, page web), par référence.
     */
    record SpecificationView(int number, String origin, Instant createdAt, String title, String summary,
            String details, List<UseCase> useCases, List<AcceptanceScenario> scenarios, List<Assumption> assumptions,
            List<Requirement> requirements, String markdown, Instant contextUpdatedAt,
            List<SpecificationContent.AdrDeviation> adrDeviations, ArchitectureView architecture,
            Map<String, String> links) {

        SpecificationView withLinks(Map<String, String> newLinks) {
            return new SpecificationView(number, origin, createdAt, title, summary, details, useCases, scenarios,
                    assumptions, requirements, markdown, contextUpdatedAt, adrDeviations, architecture, newLinks);
        }
    }

    /** Modules impactés, échanges et constats de production de la sélection enregistrée (R22, R24). */
    record ArchitectureView(List<KnowledgeSelection.ImpactedModule> modules, List<ArchitectureModel.Exchange> exchanges,
            List<ArchitectureModel.DeploymentFact> deploymentFacts) {

        static ArchitectureView of(KnowledgeSelection k) {
            return new ArchitectureView(k.modules(), k.exchanges(), k.deploymentFacts());
        }
    }

    record ProposalView(UUID proposalId, int baseVersion, SpecificationView proposed, String unifiedDiff) {

        ProposalView withLinks(Map<String, String> links) {
            return new ProposalView(proposalId, baseVersion, proposed.withLinks(links), unifiedDiff);
        }
    }

    private SessionViews() {
    }

    static SessionSummary summary(Session s, int latestVersion, LinkedJiraTicket ticket, ImplementationStatus status) {
        return new SessionSummary(s.id(), s.name(), s.state().name(), s.updatedAt(),
                latestVersion == 0 ? null : latestVersion, ticket == null ? null : ticket.issueKey(), true, status.name());
    }

    static SessionSummary summary(ManageSessions.Entry e, LinkedJiraTicket ticket) {
        SessionListing s = e.session();
        return new SessionSummary(s.id(), s.name(), s.state() == null ? null : s.state().name(), s.updatedAt(),
                e.latestVersion() == 0 ? null : e.latestVersion(), ticket == null ? null : ticket.issueKey(), s.readable(),
                s.implementationStatus().name());
    }

    static LinkedTicketView ticket(LinkedJiraTicket t) {
        return ticket(t, null);
    }

    static LinkedTicketView ticket(LinkedJiraTicket t, SessionRelation relation) {
        return t == null ? null : new LinkedTicketView(t.issueKey(), t.issueUrl(), t.syncState().name(), t.linkedVersionNumber(),
                relation == null ? null : relation.relatesTo(), relation == null ? null : relation.link().name());
    }

    static SessionDetail detail(SessionSnapshot snapshot, int latestVersion, LinkedJiraTicket ticket) {
        return detail(snapshot, latestVersion, ticket, Implementation.initial());
    }

    static SessionDetail detail(SessionSnapshot snapshot, int latestVersion, LinkedJiraTicket ticket,
            Implementation implementation) {
        Session s = snapshot.session();
        Questionnaire q = snapshot.questionnaire();
        List<String> warnings = new ArrayList<>(snapshot.warnings());
        ConcludeQuestionnaire.warnings(s, q).stream().filter(w -> !warnings.contains(w)).forEach(warnings::add);
        Questionnaire.Progress p = q.progress();
        return new SessionDetail(s.id(), s.projectId(), s.name(), s.state().name(), s.updatedAt(), latestVersion == 0 ? null : latestVersion,
                ticket == null ? null : ticket.issueKey(), ticket(ticket), s.origin().name(), s.need().text(), s.language().code(), new Progress(p.answered(), p.skipped(), p.total()),
                q.currentBatch().stream().map(SessionViews::question).toList(),
                q.treated().stream().map(SessionViews::question).toList(), warnings,
                new RevisionView(snapshot.revision().removed(), snapshot.revision().added()),
                ImplementationView.of(implementation));
    }

    static QuestionView question(Question q) {
        return new QuestionView(q.id(), q.theme().name(), q.label(), q.rationale(), q.suggestions(), q.defaultValue(),
                q.state(), q.answer() == null ? null : q.answer().value());
    }

    /** {@code pushedToJira} : c'est la dernière version envoyée vers le ticket lié. */
    static VersionSummary versionSummary(SpecificationVersion v, LinkedJiraTicket ticket) {
        boolean pushed = ticket != null && ticket.lastPushedAt() != null && ticket.linkedVersionNumber() == v.number();
        return new VersionSummary(v.number(), v.origin().name(), v.createdAt(), pushed);
    }

    static SpecificationView specification(SpecificationVersion v) {
        var c = v.content();
        return new SpecificationView(v.number(), v.origin().name(), v.createdAt(), c.title(), c.summary(), c.details(),
                c.useCases(), c.scenarios(), c.assumptions(), c.requirements(), v.markdown(), v.contextUpdatedAt(),
                c.adrDeviations(), ArchitectureView.of(v.knowledge()), Map.of());
    }

    /** La proposition est présentée comme la version qu'elle deviendra si elle est acceptée. */
    static ProposalView proposal(ProposalStore.Proposal p) {
        var c = p.content();
        return new ProposalView(p.id(), p.baseVersion(), new SpecificationView(p.baseVersion() + 1, p.origin().name(),
                p.createdAt(), c.title(), c.summary(), c.details(), c.useCases(), c.scenarios(), c.assumptions(), c.requirements(),
                p.markdown(), p.contextUpdatedAt(), c.adrDeviations(), ArchitectureView.of(p.knowledge().selection()),
                Map.of()), p.unifiedDiff());
    }
}
