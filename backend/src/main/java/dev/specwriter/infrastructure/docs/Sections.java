package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.knowledge.AdrStatus;
import dev.specwriter.domain.knowledge.KnowledgeItem;
import dev.specwriter.domain.knowledge.KnowledgeKind;
import dev.specwriter.domain.knowledge.KnowledgeRole;
import dev.specwriter.domain.port.DocumentationReader.Section;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;

/**
 * Découpe une page Antora en éléments à indexer (research.md R20) : le préambule, puis une section
 * par {@code .sect1}. Un ADR reste un seul élément. Au-delà d'environ 1 500 caractères, un élément
 * est coupé en morceaux aux limites de paragraphe.
 */
final class Sections {

    static final int CHUNK = 1_500;
    private static final int MAX_TITLE = KnowledgeItem.MAX_TITLE;
    private static final Pattern VOCABULARY = Pattern.compile("glossaire|glossary|vocabulaire|vocabulary|lexique");
    private static final Pattern OVERVIEW = Pattern.compile("^architecture\\b|vue d'ensemble|overview");
    private static final Pattern HEADING_LINE = Pattern.compile("^#{1,3} (.+)$");

    private Sections() {
    }

    static List<Section> of(String ref, String title, Element article, String text) {
        Map<String, String> parts = new LinkedHashMap<>();
        Map<String, String> anchors = new LinkedHashMap<>();
        Element preamble = article.selectFirst("> #preamble, #preamble");
        if (preamble != null) {
            String body = StructuredText.of(preamble);
            if (!body.isBlank()) {
                parts.put(title, body);
                anchors.put(title, ref);
            }
        }
        for (Element sect : article.select(".sect1")) {
            Element h2 = sect.selectFirst("> h2");
            if (h2 == null) {
                continue;
            }
            Element body = sect.selectFirst("> .sectionbody");
            String sectionTitle = h2.text().strip();
            String key = parts.containsKey(sectionTitle) ? sectionTitle + " (" + (parts.size() + 1) + ")" : sectionTitle;
            parts.put(key, body == null ? "" : StructuredText.of(body));
            anchors.put(key, h2.id().isBlank() ? ref : ref + "#" + h2.id());
        }
        KnowledgeRole pageRole = role(title, true);
        if (AdrParser.isAdr(ref, title)) {
            AdrStatus.Statement statement = AdrParser.statement(text, parts);
            String body = text.replaceFirst("^#\\s+[^\\n]*\\n+", "");
            return chunks(ref, title, body, KnowledgeKind.ADR, statement, null);
        }
        if (parts.isEmpty()) {
            String body = text.replaceFirst("^#\\s+[^\\n]*\\n+", "");
            return body.isBlank() ? List.of() : chunks(ref, title, body, KnowledgeKind.DOC_SECTION, null, pageRole);
        }
        List<Section> sections = new ArrayList<>();
        for (Map.Entry<String, String> part : parts.entrySet()) {
            if (part.getValue().isBlank()) {
                continue;
            }
            KnowledgeRole role = pageRole != null ? pageRole : role(part.getKey(), false);
            String sectionTitle = anchors.get(part.getKey()).equals(ref) ? title : part.getKey().replaceAll(" \\(\\d+\\)$", "");
            sections.addAll(chunks(anchors.get(part.getKey()), sectionTitle, part.getValue(), KnowledgeKind.DOC_SECTION,
                    null, role));
        }
        return sections;
    }

    /**
     * Document d'un dossier de documents (006) : texte structuré par {@link Extracted}, coupé aux
     * titres {@code #} à {@code ###} ; un ADR (chemin ou titre) reste un seul élément, entier. Le
     * titre d'une section reprend celui du document.
     */
    static List<Section> ofText(String ref, String title, String text) {
        List<Map.Entry<String, String>> parts = new ArrayList<>();
        String current = title;
        StringBuilder body = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            Matcher heading = HEADING_LINE.matcher(line);
            if (heading.matches()) {
                parts.add(Map.entry(current, body.toString().strip()));
                current = heading.group(1).strip();
                body.setLength(0);
            } else {
                body.append(line).append('\n');
            }
        }
        parts.add(Map.entry(current, body.toString().strip()));
        if (AdrParser.isAdr(ref, title)) {
            Map<String, String> byTitle = new LinkedHashMap<>();
            parts.forEach(e -> byTitle.putIfAbsent(e.getKey(), e.getValue()));
            return chunks(ref, title, text, KnowledgeKind.ADR, AdrParser.statement(text, byTitle), null);
        }
        KnowledgeRole pageRole = role(title, true);
        List<Section> sections = new ArrayList<>();
        int n = 0;
        for (Map.Entry<String, String> part : parts) {
            if (part.getValue().isBlank()) {
                continue;
            }
            KnowledgeRole role = pageRole != null ? pageRole : role(part.getKey(), false);
            String sectionTitle = part.getKey().equals(title) ? title : title + " — " + part.getKey();
            sections.addAll(chunks(ref + "#" + n++, sectionTitle, part.getValue(), KnowledgeKind.DOC_SECTION, null, role));
        }
        return sections;
    }

    private static KnowledgeRole role(String title, boolean page) {
        String folded = fold(title);
        if (VOCABULARY.matcher(folded).find()) {
            return KnowledgeRole.VOCABULARY;
        }
        if (page && OVERVIEW.matcher(folded).find()) {
            return KnowledgeRole.ARCHITECTURE_OVERVIEW;
        }
        return null;
    }

    private static List<Section> chunks(String ref, String title, String body, KnowledgeKind kind,
            AdrStatus.Statement adr, KnowledgeRole role) {
        String safeTitle = title == null || title.isBlank() ? ref : title.strip();
        safeTitle = safeTitle.length() > MAX_TITLE ? safeTitle.substring(0, MAX_TITLE) : safeTitle;
        List<String> pieces = split(body.strip());
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            sections.add(new Section(ref, i, safeTitle, pieces.get(i), kind, adr, role));
        }
        return sections;
    }

    /** Morceaux d'environ {@link #CHUNK} caractères, coupés entre deux paragraphes, sinon au mot. */
    static List<String> split(String body) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : body.split("\n\n")) {
            for (String part : hardSplit(paragraph)) {
                if (current.length() > 0 && current.length() + part.length() + 2 > CHUNK) {
                    pieces.add(current.toString());
                    current.setLength(0);
                }
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(part);
            }
        }
        if (current.length() > 0 || pieces.isEmpty()) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    private static List<String> hardSplit(String paragraph) {
        if (paragraph.length() <= CHUNK) {
            return List.of(paragraph);
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(paragraph.length(), start + CHUNK);
            if (end < paragraph.length()) {
                int space = paragraph.lastIndexOf(' ', end);
                end = space > start ? space : end;
            }
            parts.add(paragraph.substring(start, end).strip());
            start = end;
        }
        return parts;
    }

    private static String fold(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
