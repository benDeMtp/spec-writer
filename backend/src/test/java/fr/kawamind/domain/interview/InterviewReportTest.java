package fr.kawamind.domain.interview;

import static org.assertj.core.api.Assertions.assertThat;

import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.specification.Anchor;
import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.domain.specification.SpecificationContent;
import fr.kawamind.domain.specification.Specs;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 021, R7, FR-010 : compte rendu sans modèle, mot pour mot, en HTML échappé et en texte. */
class InterviewReportTest {

    static final Instant T0 = Instant.parse("2026-11-20T09:00:00Z");
    static final UUID S1 = new UUID(0, 1);
    static final UUID S2 = new UUID(0, 2);

    static SpecificationContent content() {
        return Specs.valid().withAssumptions(List.of(new Assumption("H-01", "Le montant est TTC.",
                Assumption.Origin.TOOL_INFERENCE, null, new Anchor("UC-01", 2, false), null)));
    }

    static InterviewPoint point(UUID session, Anchor anchor, InterviewPoint.Origin origin, String code, String text,
            InterviewPoint.Outcome outcome, String owner, LocalDate due) {
        return new InterviewPoint(UUID.randomUUID(), new UUID(0, 9), session, anchor, origin, code, text, outcome, owner,
                due, null, T0, T0);
    }

    static final List<InterviewPoint> POINTS = List.of(
            point(S1, new Anchor("UC-01", 2, false), InterviewPoint.Origin.ASSUMPTION, "H-01", "", InterviewPoint.Outcome.CONFIRMED,
                    null, null),
            point(S1, new Anchor("UC-01", 1, false), InterviewPoint.Origin.REMARK, null,
                    "Validation du manager au-delà de 500 €", InterviewPoint.Outcome.CHANGE, null, null),
            point(S1, new Anchor("UC-01", 1, false), InterviewPoint.Origin.QUESTION, null,
                    "Qui valide en l'absence du manager ?", null, "Paul D.", LocalDate.of(2026, 11, 27)));

    @Test
    void texte() {
        InterviewReport.Report r = InterviewReport.render(LocalDate.of(2026, 11, 20), List.of("Julie M.", "Paul D."),
                List.of(new InterviewReport.Section("Émission des avoirs", content(), POINTS)), Language.FR);
        assertThat(r.text()).isEqualTo("""
                Point sur « Émission des avoirs » — 20/11/2026, avec Julie M., Paul D.

                Décisions
                • H-01 (UC-01, étape 2) — confirmé : Le montant est TTC.
                • UC-01, étape 1 — à modifier : Validation du manager au-delà de 500 €

                À vérifier
                • UC-01, étape 1 — Qui valide en l'absence du manager ? (Paul D., avant le 27/11/2026)
                """);
    }

    @Test
    void passageCite() {
        InterviewPoint cited = new InterviewPoint(UUID.randomUUID(), new UUID(0, 9), S1, new Anchor("UC-01", 1, false),
                InterviewPoint.Origin.REMARK, null, "", InterviewPoint.Outcome.REMOVE, null, null, null, T0, T0,
                "et envoyé au client");
        InterviewReport.Report r = InterviewReport.render(LocalDate.of(2026, 11, 20), List.of(),
                List.of(new InterviewReport.Section("Émission des avoirs", content(), List.of(cited))), Language.FR);
        assertThat(r.text()).contains("• UC-01, étape 1, « et envoyé au client » — à retirer\n");
    }

    @Test
    void html() {
        InterviewReport.Report r = InterviewReport.render(LocalDate.of(2026, 11, 20), List.of(),
                List.of(new InterviewReport.Section("Émission des avoirs", content(), POINTS)), Language.FR);
        assertThat(r.html()).startsWith("<h2>Point sur « Émission des avoirs » — 20/11/2026</h2>")
                .contains("<h3>Décisions</h3><ul><li>H-01 (UC-01, étape 2) — confirmé : Le montant est TTC.</li>")
                .contains("<h3>À vérifier</h3>");
    }

    @Test
    void echappe() {
        InterviewPoint script = point(S1, null, InterviewPoint.Origin.REMARK, null, "<script>alert(1)</script> & co",
                InterviewPoint.Outcome.CHANGE, null, null);
        InterviewReport.Report r = InterviewReport.render(LocalDate.of(2026, 11, 20), List.of("<b>Julie</b>"),
                List.of(new InterviewReport.Section("A < B", content(), List.of(script))), Language.FR);
        assertThat(r.html()).doesNotContain("<script>").doesNotContain("<b>")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; co").contains("A &lt; B");
        // le texte brut garde le texte tel quel
        assertThat(r.text()).contains("<script>alert(1)</script> & co");
    }

    @Test
    void plusieursSpecificationsEtAucunPoint() {
        InterviewReport.Report r = InterviewReport.render(LocalDate.of(2026, 11, 20), List.of("Julie M."),
                List.of(new InterviewReport.Section("Avoirs", content(), POINTS),
                        new InterviewReport.Section("Relances", content(), List.of())), Language.FR);
        assertThat(r.text()).startsWith("Point sur « Avoirs » et « Relances » — 20/11/2026, avec Julie M.")
                .contains("\n« Avoirs »\n\nDécisions\n").contains("\n« Relances »\n\nAucun point noté.\n");
        assertThat(InterviewReport.render(LocalDate.of(2026, 11, 20), List.of(),
                List.of(new InterviewReport.Section("Avoirs", content(), List.of())), Language.FR).text())
                .endsWith("\nAucun point noté.\n");
    }

    @Test
    void pointsNonTraitesAbsentsEtAnglais() {
        InterviewPoint untouched = point(S1, null, InterviewPoint.Origin.ASSUMPTION, "H-01", "", null, null, null);
        InterviewReport.Report r = InterviewReport.render(LocalDate.of(2026, 11, 20), List.of("Julie M."),
                List.of(new InterviewReport.Section("Credit notes", content(), List.of(untouched, POINTS.get(1)))),
                Language.EN);
        assertThat(r.text()).isEqualTo("""
                Review of « Credit notes » — 2026-11-20, with Julie M.

                Decisions
                • UC-01, step 1 — to change: Validation du manager au-delà de 500 €
                """);
    }
}
