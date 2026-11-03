package dev.specwriter.infrastructure.jira;

import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.jira.converter.JiraConverterExtension;
import com.vladsch.flexmark.ast.LinkRef;
import com.vladsch.flexmark.ast.Text;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.html.renderer.NodeRenderer;
import com.vladsch.flexmark.html.renderer.NodeRenderingHandler;
import com.vladsch.flexmark.util.data.MutableDataHolder;
import java.util.Set;
import com.vladsch.flexmark.util.data.MutableDataSet;
import dev.specwriter.domain.port.WikiMarkup;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversion entre Markdown et wiki markup Jira (research.md R10). Dans le sens Markdown vers
 * wiki, flexmark-jira-converter. Dans l'autre sens (affichage, import, fusion), un convertisseur
 * limité aux éléments de FR-043a : tout autre bloc est rendu tel quel en bloc de code.
 */
@ApplicationScoped
public class JiraFormatConverter implements WikiMarkup {

    private static final Pattern HEADING = Pattern.compile("^h([1-6])\\.\\s+(.*)$");
    private static final Pattern BULLET = Pattern.compile("^([*#]+)\\s+(.*)$");
    private static final Pattern CODE_OPEN = Pattern.compile("^\\{(code|noformat)(?::([^}]*))?}\\s*$");
    private static final Pattern MACRO_OPEN = Pattern.compile("^\\{([a-zA-Z]+)(?::[^}]*)?}");
    private static final Pattern WIKI_SPECIAL = Pattern.compile("([*_\\-+^~{}\\[\\]|])");
    private static final Pattern LANGUAGE = Pattern.compile("(?:^|\\|)(?:language=)?([a-zA-Z0-9+#-]+)");
    private static final Pattern PANEL_TITLE = Pattern.compile("title=([^|}]*)");
    private static final Pattern RULE = Pattern.compile("^-{4,}$");
    private static final Pattern INLINE_CODE = Pattern.compile("\\{\\{(.+?)}}");
    private static final Pattern COLOR = Pattern.compile("\\{color(?::[^}]*)?}");
    private static final Pattern IMAGE = Pattern.compile("!([^!\\s|][^!|\\n]*?)(?:\\|[^!\\n]*)?!");
    private static final Pattern LINK = Pattern.compile("(?<!\\\\)\\[([^\\[\\]\\n]+)(?<!\\\\)]");
    private static final Pattern SAFE_URL = Pattern.compile("(?i)^(?:https?://|mailto:)\\S+$");
    private static final String PLACEHOLDER = "\u0000";
    private static final Pattern TOKEN = Pattern.compile("\u0000(\\d+)\u0000");
    /** Ligne de séparation d'un tableau Markdown : {@code | --- | :---: |}. */
    private static final Pattern GFM_SEPARATOR = Pattern.compile("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$");
    private static final Pattern MD_FENCE = Pattern.compile("^ {0,3}(```|~~~)");

    private final Parser parser;
    private final HtmlRenderer renderer;

    public JiraFormatConverter() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS,
                List.of(JiraConverterExtension.create(), TablesExtension.create(), StrikethroughExtension.create(),
                        new EscapedText()));
        parser = Parser.builder(options).build();
        renderer = HtmlRenderer.builder(options).build();
    }

    @Override
    public String toWiki(String markdown) {
        return renderer.render(parser.parse(markdown == null ? "" : markdown)).replace("{code:lang=", "{code:").strip()
                + "\n";
    }

    /** Le texte brut ne doit pas devenir de la mise en forme wiki : rendu des nœuds texte avec échappement. */
    private static final class EscapedText implements HtmlRenderer.HtmlRendererExtension {

        @Override
        public void rendererOptions(MutableDataHolder options) {
            // aucune option
        }

        @Override
        public void extend(HtmlRenderer.Builder builder, String rendererType) {
            builder.nodeRendererFactory(options -> (NodeRenderer) () -> Set.of(
                    new NodeRenderingHandler<>(Text.class,
                            (node, context, html) -> html.raw(WIKI_SPECIAL.matcher(node.getChars().unescape()).replaceAll("\\\\$1"))),
                    // [texte] sans référence définie n'est pas un lien
                    new NodeRenderingHandler<>(LinkRef.class, (node, context, html) -> {
                        if (node.isDefined()) {
                            context.delegateRender();
                        } else {
                            html.raw("\\[");
                            context.renderChildren(node);
                            html.raw("\\]");
                        }
                    })));
        }
    }

    @Override
    public String wikiTablesToMarkdown(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean code = false;
        int i = 0;
        while (i < lines.length) {
            if (MD_FENCE.matcher(lines[i]).find()) {
                code = !code;
            }
            if (code || !lines[i].startsWith("|")) {
                out.append(lines[i++]).append('\n');
                continue;
            }
            int end = i;
            while (end < lines.length && lines[end].startsWith("|")) {
                end++;
            }
            List<String> block = List.of(lines).subList(i, end);
            if (block.stream().anyMatch(l -> GFM_SEPARATOR.matcher(l).matches())) {
                block.forEach(l -> out.append(l).append('\n'));
            } else {
                if (!out.isEmpty() && !out.toString().endsWith("\n\n")) {
                    out.append('\n');
                }
                out.append(new WikiReader(String.join("\n", block), null).read());
                if (end < lines.length && !lines[end].isBlank()) {
                    out.append('\n');
                }
            }
            i = end;
        }
        String result = out.toString();
        return markdown.endsWith("\n") ? result : result.substring(0, result.length() - 1);
    }

    @Override
    public String toMarkdown(String wiki, String issueUrl) {
        if (wiki == null || wiki.isBlank()) {
            return "";
        }
        return new WikiReader(wiki, issueUrl).read();
    }

    /**
     * Wiki markup vers Markdown (FR-043a) : titres, listes, mises en forme, tableaux, liens sûrs,
     * citations, panneaux et blocs de code. Images, pièces jointes et mentions deviennent un renvoi
     * vers le ticket, sans rien télécharger. Tout autre bloc reste tel quel, en bloc de code.
     */
    private static final class WikiReader {

        private final String[] lines;
        private final String issueUrl;
        private final StringBuilder out = new StringBuilder();
        private int i;

        WikiReader(String wiki, String issueUrl) {
            this.lines = wiki.replace("\r\n", "\n").split("\n", -1);
            this.issueUrl = issueUrl;
        }

        String read() {
            while (i < lines.length) {
                String line = lines[i];
                String trimmed = line.strip();
                Matcher code = CODE_OPEN.matcher(trimmed);
                Matcher macro = MACRO_OPEN.matcher(trimmed);
                if (code.matches()) {
                    code(code);
                } else if (trimmed.startsWith("{quote}")) {
                    quote(null, trimmed.substring("{quote}".length()), "{quote}");
                } else if (macro.find() && "panel".equals(macro.group(1))) {
                    Matcher title = PANEL_TITLE.matcher(macro.group());
                    quote(title.find() ? title.group(1).strip() : null, trimmed.substring(macro.end()), "{panel}");
                } else if (macro.find(0) && !"color".equals(macro.group(1))) {
                    rawBlock(line, macro);
                } else if (line.startsWith("|")) {
                    table();
                } else if (RULE.matcher(trimmed).matches()) {
                    block("---");
                    i++;
                } else if (line.startsWith("bq. ")) {
                    block("> " + inline(line.substring(4), issueUrl));
                    i++;
                } else {
                    out.append(line(line, issueUrl)).append('\n');
                    i++;
                }
            }
            return out.toString().strip() + "\n";
        }

        private void code(Matcher code) {
            String tag = "{" + code.group(1) + "}";
            out.append("```").append(language(code.group(2))).append('\n');
            i++;
            while (i < lines.length && !lines[i].strip().equals(tag)) {
                out.append(lines[i++]).append('\n');
            }
            out.append("```\n");
            i++;
        }

        private void rawBlock(String line, Matcher macro) {
            String tag = "{" + macro.group(1) + "}";
            out.append("```\n").append(line).append('\n');
            boolean closed = line.strip().length() > macro.end() && line.strip().endsWith(tag);
            i++;
            while (!closed && i < lines.length) {
                out.append(lines[i]).append('\n');
                closed = lines[i].strip().endsWith(tag);
                i++;
            }
            out.append("```\n");
        }

        /** {@code {quote}} et {@code {panel}} : bloc de citation, titre du panneau en gras. */
        private void quote(String title, String rest, String tag) {
            List<String> content = new ArrayList<>();
            String current = rest;
            i++;
            while (true) {
                int end = current.indexOf(tag);
                if (end >= 0) {
                    content.add(current.substring(0, end));
                    break;
                }
                content.add(current);
                if (i >= lines.length) {
                    break;
                }
                current = lines[i++];
            }
            StringBuilder quoted = new StringBuilder();
            if (title != null && !title.isEmpty()) {
                quoted.append("> **").append(inline(title, issueUrl)).append("**\n>\n");
            }
            content.stream().map(String::strip).filter(l -> !l.isEmpty())
                    .forEach(l -> quoted.append("> ").append(line(l, issueUrl)).append('\n'));
            block(quoted.toString().strip());
        }

        /** Bloc séparé par des lignes vides : sinon le Markdown le fondrait dans le paragraphe voisin. */
        private void block(String markdown) {
            if (!out.isEmpty() && !out.toString().endsWith("\n\n")) {
                out.append('\n');
            }
            out.append(markdown).append("\n\n");
        }

        private void table() {
            List<String> rows = new ArrayList<>();
            while (i < lines.length && lines[i].startsWith("|")) {
                StringBuilder row = new StringBuilder(lines[i++].stripTrailing());
                // une cellule continue sur la ligne suivante tant que la ligne ne se ferme pas par |
                while (!closesRow(row) && i < lines.length && !lines[i].startsWith("|")) {
                    row.append('\n').append(lines[i++].stripTrailing());
                }
                rows.add(row.toString());
            }
            List<List<Cell>> cells = rows.stream().map(this::cells).toList();
            int width = cells.stream().mapToInt(List::size).max().orElse(0);
            boolean header = cells.getFirst().stream().allMatch(Cell::header);
            StringBuilder md = new StringBuilder();
            md.append(row(header ? cells.getFirst() : List.of(), width, false));
            md.append("|").append(" --- |".repeat(width)).append('\n');
            for (List<Cell> r : header ? cells.subList(1, cells.size()) : cells) {
                md.append(row(r, width, true));
            }
            block(md.toString().strip());
        }

        private static boolean closesRow(CharSequence row) {
            String r = row.toString();
            return r.endsWith("|") && !r.endsWith("\\|");
        }

        private List<Cell> cells(String row) {
            List<String> tokens = new ArrayList<>();
            String protectedRow = protect(row.replace("\\\\", "\n"), tokens, issueUrl);
            List<Cell> result = new ArrayList<>();
            int pos = 0;
            while (pos < protectedRow.length() && protectedRow.charAt(pos) == '|') {
                boolean header = protectedRow.startsWith("||", pos);
                int start = pos + (header ? 2 : 1);
                int end = nextSeparator(protectedRow, start);
                if (end == start && end >= protectedRow.length()) {
                    break;
                }
                String text = protectedRow.substring(start, Math.min(end, protectedRow.length()));
                result.add(new Cell(restore(format(text.strip()), tokens).replace("\n", "<br>"), header));
                pos = end;
            }
            return result;
        }

        private static int nextSeparator(String row, int from) {
            for (int p = from; p < row.length(); p++) {
                if (row.charAt(p) == '\\') {
                    p++;
                } else if (row.charAt(p) == '|') {
                    return p;
                }
            }
            return row.length();
        }

        private static String row(List<Cell> cells, int width, boolean boldHeaders) {
            StringBuilder md = new StringBuilder("|");
            for (int c = 0; c < width; c++) {
                Cell cell = c < cells.size() ? cells.get(c) : new Cell("", false);
                String text = boldHeaders && cell.header() && !cell.text().isEmpty() ? "**" + cell.text() + "**" : cell.text();
                md.append(' ').append(text).append(" |");
            }
            return md.append('\n').toString();
        }

        private record Cell(String text, boolean header) {
        }
    }

    private static String language(String params) {
        if (params == null) {
            return "";
        }
        Matcher m = LANGUAGE.matcher(params);
        return m.find() && !m.group(1).contains("=") ? m.group(1) : "";
    }

    private static String line(String line, String issueUrl) {
        Matcher h = HEADING.matcher(line);
        if (h.matches()) {
            return "#".repeat(Integer.parseInt(h.group(1))) + " " + inline(h.group(2), issueUrl);
        }
        Matcher b = BULLET.matcher(line);
        if (b.matches() && !isBoldOnly(line)) {
            String marks = b.group(1);
            String indent = "  ".repeat(marks.length() - 1);
            return indent + (marks.endsWith("#") ? "1. " : "- ") + inline(b.group(2), issueUrl);
        }
        return inline(line, issueUrl);
    }

    /** {@code *gras* suite} commence par une étoile sans être une puce. */
    private static boolean isBoldOnly(String line) {
        return line.startsWith("*") && !line.startsWith("* ") && !line.startsWith("**");
    }

    private static String inline(String text, String issueUrl) {
        List<String> tokens = new ArrayList<>();
        return restore(format(protect(text, tokens, issueUrl)), tokens);
    }

    /**
     * Met de côté code en ligne, liens, images, pièces jointes et mentions, que la mise en forme ne
     * doit pas toucher (une URL contient souvent {@code _}), et retire les couleurs.
     */
    private static String protect(String text, List<String> tokens, String issueUrl) {
        String t = INLINE_CODE.matcher(text).replaceAll(m -> token(tokens, "`" + m.group(1) + "`"));
        t = COLOR.matcher(t).replaceAll("");
        t = IMAGE.matcher(t).replaceAll(m -> token(tokens, reference("image", m.group(1).strip(), issueUrl)));
        return LINK.matcher(t).replaceAll(m -> token(tokens, link(m.group(1), issueUrl)));
    }

    private static String link(String content, String issueUrl) {
        if (content.startsWith("~")) {
            return reference("mention", content.substring(1), issueUrl);
        }
        if (content.startsWith("^")) {
            return reference("pièce jointe", content.substring(1), issueUrl);
        }
        int bar = content.indexOf('|');
        String text = bar < 0 ? null : content.substring(0, bar).strip();
        String rest = bar < 0 ? content : content.substring(bar + 1);
        int tip = rest.indexOf('|');
        String url = (tip < 0 ? rest : rest.substring(0, tip)).strip();
        if (SAFE_URL.matcher(url).matches()) {
            return text == null || text.isEmpty() ? "<" + url + ">" : "[" + text + "](" + url + ")";
        }
        return text == null || text.isEmpty() ? url : text + " (" + url + ")";
    }

    /** Élément non affiché : nommé, avec un renvoi vers le ticket quand son adresse est connue. */
    private static String reference(String kind, String name, String issueUrl) {
        String label = kind + " : " + name;
        return issueUrl == null ? label : "[" + label + "](" + issueUrl + ")";
    }

    private static String token(List<String> tokens, String value) {
        tokens.add(value);
        return Matcher.quoteReplacement(PLACEHOLDER + (tokens.size() - 1) + PLACEHOLDER);
    }

    private static String restore(String text, List<String> tokens) {
        return TOKEN.matcher(text).replaceAll(m -> Matcher.quoteReplacement(tokens.get(Integer.parseInt(m.group(1)))));
    }

    private static String format(String text) {
        return text
                .replaceAll("(?<![\\w*])\\*(?=\\S)(.+?)(?<=\\S)\\*(?![\\w*])", "**$1**")
                .replaceAll("(?<![\\w_])_(?=\\S)(.+?)(?<=\\S)_(?![\\w_])", "*$1*")
                .replaceAll("(?<![\\w-])-(?=[^\\s-])(.+?)(?<=[^\\s-])-(?![\\w-])", "~~$1~~")
                .replaceAll("(?<![\\w+])\\+(?=[^\\s+])(.+?)(?<=[^\\s+])\\+(?![\\w+])", "<u>$1</u>");
    }
}
