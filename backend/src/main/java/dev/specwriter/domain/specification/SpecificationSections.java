package dev.specwriter.domain.specification;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sections du Markdown d'une version, pour la discussion de la spécification (011, research.md R2) :
 * une section par rubrique (titre de niveau 2) et par use case (titre de niveau 3). Chaque section
 * porte l'ancre de l'écran de la spécification : {@code summary}, {@code use-cases}, {@code UC-01},
 * {@code scenarios-UC-01}… Le texte des sections recompose le document.
 */
public final class SpecificationSections {

    private static final Pattern HEADING = Pattern.compile("^(#{2,3}) (.+)$", Pattern.MULTILINE);
    private static final Pattern USE_CASE = Pattern.compile("^(\\S+?)\\s?:\\s");

    /** {@code partial} : section réduite à son titre, faute de place. */
    public record Section(String anchor, String title, String text, boolean partial) {

        @Override
        public String toString() {
            return "Section[" + anchor + ", " + text.length() + " caractères" + (partial ? ", réduite" : "") + "]";
        }
    }

    private SpecificationSections() {
    }

    public static List<Section> of(String markdown) {
        String md = markdown == null ? "" : markdown;
        Map<String, String> anchors = MarkdownRenderer.anchors();
        List<Section> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        Matcher m = HEADING.matcher(md);
        int start = 0;
        String anchor = "introduction";
        String title = firstLine(md);
        String parent = "";
        while (m.find()) {
            if (m.start() > start || start == 0) {
                add(out, used, anchor, title, md.substring(start, m.start()));
            }
            String heading = m.group(2).strip();
            if (m.group(1).length() == 2) {
                parent = anchors.getOrDefault(heading, slug(heading));
                anchor = parent;
            } else {
                Matcher uc = USE_CASE.matcher(heading);
                String code = uc.find() ? uc.group(1) : slug(heading);
                anchor = switch (parent) {
                    case "use-cases" -> code;
                    case "scenarios" -> "scenarios-" + code;
                    default -> parent + "-" + slug(heading);
                };
            }
            title = heading;
            start = m.start();
        }
        add(out, used, anchor, title, md.substring(start));
        return List.copyOf(out);
    }

    /**
     * Garde le texte des premières sections tant qu'elles tiennent dans {@code room} caractères ; les
     * suivantes sont réduites à leur titre.
     */
    public static List<Section> fit(List<Section> sections, int room) {
        List<Section> out = new ArrayList<>();
        int left = room;
        boolean full = false;
        for (Section s : sections) {
            if (!full && s.text().length() <= left) {
                left -= s.text().length();
                out.add(s);
            } else {
                full = true;
                out.add(new Section(s.anchor(), s.title(), s.title(), true));
            }
        }
        return List.copyOf(out);
    }

    private static void add(List<Section> out, Set<String> used, String anchor, String title, String text) {
        if (text.isEmpty()) {
            return;
        }
        String unique = anchor;
        for (int i = 2; !used.add(unique); i++) {
            unique = anchor + "-" + i;
        }
        out.add(new Section(unique, title, text, false));
    }

    private static String firstLine(String md) {
        String line = md.lines().findFirst().orElse("");
        return line.startsWith("# ") ? line.substring(2).strip() : line.strip();
    }

    static String slug(String heading) {
        String plain = Normalizer.normalize(heading, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "section" : slug;
    }
}
