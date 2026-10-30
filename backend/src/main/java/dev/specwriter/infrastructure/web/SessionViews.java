package dev.specwriter.infrastructure.web;

import dev.specwriter.application.session.ConcludeQuestionnaire;
import dev.specwriter.application.session.SessionSnapshot;
import dev.specwriter.domain.question.Question;
import dev.specwriter.domain.question.QuestionState;
import dev.specwriter.domain.question.Questionnaire;
import dev.specwriter.domain.scenario.AcceptanceScenario;
import dev.specwriter.domain.session.Session;
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
            String jiraKey) {
    }

    record SessionDetail(UUID id, String name, String state, Instant updatedAt, Integer latestVersion, String jiraKey,
            String need, String language, Progress progress, List<QuestionView> currentBatch,
            List<QuestionView> answeredQuestions, List<String> warnings) {
    }

    record VersionSummary(int number, String origin, Instant createdAt, boolean pushedToJira) {
    }

    record SpecificationView(int number, String origin, Instant createdAt, String title, String summary,
            List<UseCase> useCases, List<AcceptanceScenario> scenarios, List<Assumption> assumptions,
            List<Requirement> requirements, String markdown) {
    }

    private SessionViews() {
    }

    static SessionSummary summary(Session s, int latestVersion) {
        return new SessionSummary(s.id(), s.name(), s.state().name(), s.updatedAt(),
                latestVersion == 0 ? null : latestVersion, null);
    }

    static SessionDetail detail(SessionSnapshot snapshot, int latestVersion) {
        Session s = snapshot.session();
        Questionnaire q = snapshot.questionnaire();
        List<String> warnings = new ArrayList<>(snapshot.warnings());
        ConcludeQuestionnaire.warnings(s, q).stream().filter(w -> !warnings.contains(w)).forEach(warnings::add);
        Questionnaire.Progress p = q.progress();
        return new SessionDetail(s.id(), s.name(), s.state().name(), s.updatedAt(), latestVersion == 0 ? null : latestVersion,
                null, s.need().text(), s.language().code(), new Progress(p.answered(), p.skipped(), p.total()),
                q.currentBatch().stream().map(SessionViews::question).toList(),
                q.treated().stream().map(SessionViews::question).toList(), warnings);
    }

    static QuestionView question(Question q) {
        return new QuestionView(q.id(), q.theme().name(), q.label(), q.rationale(), q.suggestions(), q.defaultValue(),
                q.state(), q.answer() == null ? null : q.answer().value());
    }

    static VersionSummary versionSummary(SpecificationVersion v) {
        return new VersionSummary(v.number(), v.origin().name(), v.createdAt(), false);
    }

    static SpecificationView specification(SpecificationVersion v) {
        var c = v.content();
        return new SpecificationView(v.number(), v.origin().name(), v.createdAt(), c.title(), c.summary(), c.useCases(),
                c.scenarios(), c.assumptions(), c.requirements(), v.markdown());
    }
}
