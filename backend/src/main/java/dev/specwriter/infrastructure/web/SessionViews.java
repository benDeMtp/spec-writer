package dev.specwriter.infrastructure.web;

import dev.specwriter.application.session.ConcludeQuestionnaire;
import dev.specwriter.application.session.ManageSessions;
import dev.specwriter.application.session.SessionSnapshot;
import dev.specwriter.application.specification.ProposalStore;
import dev.specwriter.domain.jira.LinkedJiraTicket;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.QuestionState;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.scenario.AcceptanceScenario;
import dev.specwriter.domain.session.Session;
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
            String jiraKey, boolean readable) {
    }

    record RevisionView(List<String> removed, List<String> added) {
    }

    record LinkedTicketView(String issueKey, String issueUrl, String syncState, int linkedVersionNumber) {
    }

    record SessionDetail(UUID id, String name, String state, Instant updatedAt, Integer latestVersion, String jiraKey,
            LinkedTicketView linkedTicket, String origin, String need, String language, Progress progress, List<QuestionView> currentBatch,
            List<QuestionView> answeredQuestions, List<String> warnings, RevisionView revision) {
    }

    record VersionSummary(int number, String origin, Instant createdAt, boolean pushedToJira) {
    }

    record SpecificationView(int number, String origin, Instant createdAt, String title, String summary,
            String details, List<UseCase> useCases, List<AcceptanceScenario> scenarios, List<Assumption> assumptions,
            List<Requirement> requirements, String markdown) {
    }

    record ProposalView(UUID proposalId, int baseVersion, SpecificationView proposed, String unifiedDiff) {
    }

    private SessionViews() {
    }

    static SessionSummary summary(Session s, int latestVersion, LinkedJiraTicket ticket) {
        return new SessionSummary(s.id(), s.name(), s.state().name(), s.updatedAt(),
                latestVersion == 0 ? null : latestVersion, ticket == null ? null : ticket.issueKey(), true);
    }

    static SessionSummary summary(ManageSessions.Entry e, LinkedJiraTicket ticket) {
        SessionListing s = e.session();
        return new SessionSummary(s.id(), s.name(), s.state() == null ? null : s.state().name(), s.updatedAt(),
                e.latestVersion() == 0 ? null : e.latestVersion(), ticket == null ? null : ticket.issueKey(), s.readable());
    }

    static LinkedTicketView ticket(LinkedJiraTicket t) {
        return t == null ? null : new LinkedTicketView(t.issueKey(), t.issueUrl(), t.syncState().name(), t.linkedVersionNumber());
    }

    static SessionDetail detail(SessionSnapshot snapshot, int latestVersion, LinkedJiraTicket ticket) {
        Session s = snapshot.session();
        Questionnaire q = snapshot.questionnaire();
        List<String> warnings = new ArrayList<>(snapshot.warnings());
        ConcludeQuestionnaire.warnings(s, q).stream().filter(w -> !warnings.contains(w)).forEach(warnings::add);
        Questionnaire.Progress p = q.progress();
        return new SessionDetail(s.id(), s.name(), s.state().name(), s.updatedAt(), latestVersion == 0 ? null : latestVersion,
                ticket == null ? null : ticket.issueKey(), ticket(ticket), s.origin().name(), s.need().text(), s.language().code(), new Progress(p.answered(), p.skipped(), p.total()),
                q.currentBatch().stream().map(SessionViews::question).toList(),
                q.treated().stream().map(SessionViews::question).toList(), warnings,
                new RevisionView(snapshot.revision().removed(), snapshot.revision().added()));
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
                c.useCases(), c.scenarios(), c.assumptions(), c.requirements(), v.markdown());
    }

    /** La proposition est présentée comme la version qu'elle deviendra si elle est acceptée. */
    static ProposalView proposal(ProposalStore.Proposal p) {
        var c = p.content();
        return new ProposalView(p.id(), p.baseVersion(), new SpecificationView(p.baseVersion() + 1, p.origin().name(),
                p.createdAt(), c.title(), c.summary(), c.details(), c.useCases(), c.scenarios(), c.assumptions(), c.requirements(),
                p.markdown()), p.unifiedDiff());
    }
}
