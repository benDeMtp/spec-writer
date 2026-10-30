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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversion entre Markdown et wiki markup Jira (research.md R10). Dans le sens Markdown vers
 * wiki, flexmark-jira-converter. Dans l'autre sens, réservé à l'affichage, un convertisseur réduit
 * aux titres, listes, gras, italique et code : tout autre bloc est rendu tel quel en bloc de code.
 */
@ApplicationScoped
public class JiraFormatConverter implements WikiMarkup {

    private static final Pattern HEADING = Pattern.compile("^h([1-6])\\.\\s+(.*)$");
    private static final Pattern BULLET = Pattern.compile("^([*#]+)\\s+(.*)$");
    private static final Pattern CODE_OPEN = Pattern.compile("^\\{(code|noformat)(?::([^}]*))?}\\s*$");
    private static final Pattern MACRO_OPEN = Pattern.compile("^\\{([a-zA-Z]+)(?::[^}]*)?}");
    private static final Pattern WIKI_SPECIAL = Pattern.compile("([*_\\-+^~{}\\[\\]|])");
    private static final Pattern LANGUAGE = Pattern.compile("(?:^|\\|)(?:language=)?([a-zA-Z0-9+#-]+)");

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
    public String toMarkdown(String wiki) {
        if (wiki == null || wiki.isBlank()) {
            return "";
        }
        String[] lines = wiki.replace("\r\n", "\n").split("\n", -1);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            Matcher code = CODE_OPEN.matcher(line.trim());
            Matcher macro = MACRO_OPEN.matcher(line.trim());
            if (code.matches()) {
                String tag = "{" + code.group(1) + "}";
                out.append("```").append(language(code.group(2))).append('\n');
                i++;
                while (i < lines.length && !lines[i].trim().equals(tag)) {
                    out.append(lines[i++]).append('\n');
                }
                out.append("```\n");
                i++;
            } else if (macro.find()) {
                String tag = "{" + macro.group(1) + "}";
                out.append("```\n").append(line).append('\n');
                boolean closed = line.trim().length() > macro.end() && line.trim().endsWith(tag);
                i++;
                while (!closed && i < lines.length) {
                    out.append(lines[i]).append('\n');
                    closed = lines[i].trim().endsWith(tag);
                    i++;
                }
                out.append("```\n");
            } else if (line.startsWith("|")) {
                out.append("```\n");
                while (i < lines.length && lines[i].startsWith("|")) {
                    out.append(lines[i++]).append('\n');
                }
                out.append("```\n");
            } else {
                out.append(line(line)).append('\n');
                i++;
            }
        }
        return out.toString().strip() + "\n";
    }

    private static String language(String params) {
        if (params == null) {
            return "";
        }
        Matcher m = LANGUAGE.matcher(params);
        return m.find() && !m.group(1).contains("=") ? m.group(1) : "";
    }

    private static String line(String line) {
        Matcher h = HEADING.matcher(line);
        if (h.matches()) {
            return "#".repeat(Integer.parseInt(h.group(1))) + " " + inline(h.group(2));
        }
        Matcher b = BULLET.matcher(line);
        if (b.matches() && !isBoldOnly(line)) {
            String marks = b.group(1);
            String indent = "  ".repeat(marks.length() - 1);
            return indent + (marks.endsWith("#") ? "1. " : "- ") + inline(b.group(2));
        }
        return inline(line);
    }

    /** {@code *gras* suite} commence par une étoile sans être une puce. */
    private static boolean isBoldOnly(String line) {
        return line.startsWith("*") && !line.startsWith("* ") && !line.startsWith("**");
    }

    private static String inline(String text) {
        return text
                .replaceAll("\\{\\{(.+?)}}", "`$1`")
                .replaceAll("(?<![\\w*])\\*(?=\\S)(.+?)(?<=\\S)\\*(?![\\w*])", "**$1**")
                .replaceAll("(?<![\\w_])_(?=\\S)(.+?)(?<=\\S)_(?![\\w_])", "*$1*");
    }
}
