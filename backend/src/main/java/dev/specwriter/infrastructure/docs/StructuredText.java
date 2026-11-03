package dev.specwriter.infrastructure.docs;

import java.util.List;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Contenu d'une page Antora en texte structuré, proche du Markdown : titres ({@code #}),
 * paragraphes, listes ({@code -}) et tableaux ({@code | a | b |}). Rien n'est exécuté ni interprété :
 * seul le texte des éléments est repris.
 */
final class StructuredText {

    private StructuredText() {
    }

    static String of(Element article) {
        StringBuilder out = new StringBuilder();
        walk(article, out, 0);
        return out.toString().replaceAll("\n{3,}", "\n\n").strip();
    }

    private static void walk(Element element, StringBuilder out, int depth) {
        for (Element child : element.children()) {
            String tag = child.normalName();
            switch (tag) {
                case "h1", "h2", "h3", "h4", "h5", "h6" -> block(out, "#".repeat(tag.charAt(1) - '0') + " " + child.text());
                case "p", "pre" -> block(out, child.text());
                case "ul", "ol" -> list(child, out, depth);
                case "table" -> table(child, out);
                case "dl" -> definitions(child, out);
                case "script", "style", "nav", "form", "button" -> {
                    // jamais repris
                }
                default -> walk(child, out, depth);
            }
        }
    }

    private static void list(Element list, StringBuilder out, int depth) {
        for (Element li : list.children()) {
            if (!"li".equals(li.normalName())) {
                continue;
            }
            Element copy = li.clone();
            Elements nested = copy.select("> ul, > ol, > div > ul, > div > ol");
            nested.remove();
            out.append("  ".repeat(depth)).append("- ").append(copy.text()).append('\n');
            for (Element sub : li.select("> ul, > ol, > div > ul, > div > ol")) {
                list(sub, out, depth + 1);
            }
        }
        out.append('\n');
    }

    private static void table(Element table, StringBuilder out) {
        for (Element row : table.select("tr")) {
            List<String> cells = row.select("> th, > td").stream().map(c -> c.text().replace("|", "\\|")).toList();
            if (!cells.isEmpty()) {
                out.append("| ").append(String.join(" | ", cells)).append(" |\n");
            }
        }
        out.append('\n');
    }

    private static void definitions(Element dl, StringBuilder out) {
        for (Element item : dl.children()) {
            if ("dt".equals(item.normalName())) {
                out.append("- ").append(item.text()).append(" :");
            } else if ("dd".equals(item.normalName())) {
                out.append(' ').append(item.text()).append('\n');
            }
        }
        out.append('\n');
    }

    private static void block(StringBuilder out, String text) {
        if (!text.isBlank()) {
            out.append(text.strip()).append("\n\n");
        }
    }
}
