package fr.kawamind.domain.interview;

import fr.kawamind.domain.session.Language;
import fr.kawamind.domain.specification.Assumption;
import fr.kawamind.domain.specification.OpenQuestion;
import fr.kawamind.domain.specification.SourceDeviation;
import fr.kawamind.domain.specification.SpecificationContent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compte rendu d'une séance (021, research.md R7, FR-010) : construit sans modèle depuis les points,
 * mot pour mot, en HTML (titres et listes gardés par un mail ou Teams, texte échappé) et en texte brut.
 * Les points de la spécification non traités n'y figurent pas.
 */
public final class InterviewReport {

    /** Une spécification de la séance : son nom, sa version relue, ses points, ses sources à mettre à jour. */
    public record Section(String name, SpecificationContent content, List<InterviewPoint> points, List<SourceLine> sources) {

        public Section(String name, SpecificationContent content, List<InterviewPoint> points) {
            this(name, content, points, List.of());
        }
    }

    /** Une source de vérité à mettre à jour : son titre et le texte de la décision. */
    public record SourceLine(String title, String text) {
    }

    public record Report(String html, String text) {
    }

    private record Labels(String review, String with, String and, String decisions, String toCheck, String sources,
            String empty, String colon, String before, DateTimeFormatter date, Map<InterviewPoint.Outcome, String> outcomes) {
    }

    private static final Labels FR = new Labels("Point sur", "avec", "et", "Décisions", "À vérifier",
            "Sources à mettre à jour", "Aucun point noté.", " : ", "avant le", DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            Map.of(InterviewPoint.Outcome.CONFIRMED, "confirmé", InterviewPoint.Outcome.CHANGE, "à modifier",
                    InterviewPoint.Outcome.REMOVE, "à retirer", InterviewPoint.Outcome.DEFERRED, "reporté"));
    private static final Labels EN = new Labels("Review of", "with", "and", "Decisions", "To check", "Sources to update",
            "No point noted.", ": ", "by", DateTimeFormatter.ISO_LOCAL_DATE,
            Map.of(InterviewPoint.Outcome.CONFIRMED, "confirmed", InterviewPoint.Outcome.CHANGE, "to change",
                    InterviewPoint.Outcome.REMOVE, "to remove", InterviewPoint.Outcome.DEFERRED, "deferred"));

    private InterviewReport() {
    }

    public static Report render(LocalDate date, List<String> participants, List<Section> sections, Language language) {
        Labels l = language == Language.EN ? EN : FR;
        String title = title(date, participants, sections, l);
        StringBuilder html = new StringBuilder("<h2>").append(escape(title)).append("</h2>");
        List<String> blocks = new ArrayList<>();
        blocks.add(title);
        boolean several = sections.size() > 1;
        String heading = several ? "h4" : "h3";
        for (Section section : sections) {
            if (several) {
                String name = quoted(section.name());
                blocks.add(name);
                html.append("<h3>").append(escape(name)).append("</h3>");
            }
            Map<String, String> statements = statements(section.content());
            List<String> decisions = new ArrayList<>();
            List<String> toCheck = new ArrayList<>();
            for (InterviewPoint p : section.points()) {
                if (p.outcome() == null) {
                    continue;
                }
                if (p.outcome() == InterviewPoint.Outcome.DEFERRED) {
                    toCheck.add(deferred(p, l, language));
                } else {
                    decisions.add(decision(p, statements, l, language));
                }
            }
            List<String> sources = section.sources().stream()
                    .map(s -> s.title() + (s.text().isBlank() ? "" : l.colon() + s.text())).toList();
            if (decisions.isEmpty() && toCheck.isEmpty() && sources.isEmpty()) {
                blocks.add(l.empty());
                html.append("<p>").append(escape(l.empty())).append("</p>");
                continue;
            }
            list(l.decisions(), decisions, heading, blocks, html);
            list(l.toCheck(), toCheck, heading, blocks, html);
            list(l.sources(), sources, heading, blocks, html);
        }
        return new Report(html.toString(), String.join("\n\n", blocks) + "\n");
    }

    private static void list(String title, List<String> lines, String heading, List<String> blocks, StringBuilder html) {
        if (lines.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder(title);
        html.append('<').append(heading).append('>').append(escape(title)).append("</").append(heading).append("><ul>");
        for (String line : lines) {
            text.append("\n• ").append(line);
            html.append("<li>").append(escape(line)).append("</li>");
        }
        html.append("</ul>");
        blocks.add(text.toString());
    }

    private static String title(LocalDate date, List<String> participants, List<Section> sections, Labels l) {
        List<String> names = sections.stream().map(s -> quoted(s.name())).toList();
        String joined = names.size() <= 1 ? String.join("", names)
                : String.join(", ", names.subList(0, names.size() - 1)) + " " + l.and() + " " + names.getLast();
        String title = l.review() + " " + joined + " — " + l.date().format(date);
        return participants.isEmpty() ? title : title + ", " + l.with() + " " + String.join(", ", participants);
    }

    private static String quoted(String name) {
        return "« " + name + " »";
    }

    /** « H-01 (UC-01, étape 2) — confirmé : … » ; « UC-01, étape 1 — à modifier : … ». */
    private static String decision(InterviewPoint p, Map<String, String> statements, Labels l, Language language) {
        String detail = !p.text().isEmpty() ? p.text() : statements.getOrDefault(p.originCode(), "");
        String head = prefix(p, language);
        String outcome = l.outcomes().get(p.outcome());
        String line = head.isEmpty() ? outcome : head + " — " + outcome;
        return detail.isEmpty() ? line : line + l.colon() + detail;
    }

    /** « UC-01, étape 1 — Qui valide ? (Paul D., avant le 27/11/2026) ». */
    private static String deferred(InterviewPoint p, Labels l, Language language) {
        String head = prefix(p, language);
        String line = head.isEmpty() ? p.text() : head + " — " + p.text();
        List<String> who = new ArrayList<>();
        if (p.owner() != null) {
            who.add(p.owner());
        }
        if (p.due() != null) {
            who.add(l.before() + " " + l.date().format(p.due()));
        }
        return who.isEmpty() ? line : line + " (" + String.join(", ", who) + ")";
    }

    private static String prefix(InterviewPoint p, Language language) {
        String anchor = InterviewPoint.cited(p.anchor() == null ? "" : p.anchor().label(language), p.quote(), language);
        if (p.originCode() == null) {
            return anchor;
        }
        return anchor.isEmpty() ? p.originCode() : p.originCode() + " (" + anchor + ")";
    }

    private static Map<String, String> statements(SpecificationContent content) {
        Map<String, String> statements = new HashMap<>();
        if (content == null) {
            return statements;
        }
        for (Assumption a : content.assumptions()) {
            statements.put(a.code(), a.statement());
        }
        for (SourceDeviation d : content.sourceDeviations()) {
            statements.put(d.code(), d.difference());
        }
        for (OpenQuestion q : content.openQuestions()) {
            statements.put(q.code(), q.question());
        }
        return statements;
    }

    private static String escape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
