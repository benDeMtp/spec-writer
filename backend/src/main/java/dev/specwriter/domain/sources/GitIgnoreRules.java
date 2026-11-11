package dev.specwriter.domain.sources;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Règles d'un fichier {@code .gitignore}, rattachées au dossier qui le contient (013, research.md
 * R3) : commentaires, échappements {@code \#} et {@code \!}, négation {@code !}, motif ancré (une
 * {@code /} en tête ou au milieu), dossier seulement ({@code /} final), {@code *}, {@code **},
 * {@code ?} et classes {@code […]}. Dans un même fichier, la dernière règle qui correspond l'emporte.
 */
public final class GitIgnoreRules {

    /** Verdict d'un fichier pour un chemin. */
    public enum Match {
        IGNORED,
        INCLUDED,
        NONE
    }

    private record Rule(Pattern pattern, boolean negated, boolean directoryOnly) {
    }

    private final String base;
    private final List<Rule> rules;

    private GitIgnoreRules(String base, List<Rule> rules) {
        this.base = base;
        this.rules = rules;
    }

    /**
     * Lit le contenu d'un {@code .gitignore} situé dans {@code baseDir}, chemin relatif à la racine
     * ({@code ""} pour la racine).
     */
    public static GitIgnoreRules parse(String baseDir, String content) {
        String base = baseDir == null ? "" : baseDir.replace('\\', '/');
        List<Rule> rules = new ArrayList<>();
        for (String raw : content.split("\r?\n")) {
            Rule rule = rule(raw);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return new GitIgnoreRules(base, List.copyOf(rules));
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /** Verdict pour un chemin relatif à la racine ; {@code NONE} hors du dossier de ce fichier. */
    public Match match(String relativePath, boolean directory) {
        String path = relativePath.replace('\\', '/');
        if (!base.isEmpty()) {
            if (!path.startsWith(base + "/")) {
                return Match.NONE;
            }
            path = path.substring(base.length() + 1);
        }
        for (int i = rules.size() - 1; i >= 0; i--) {
            Rule rule = rules.get(i);
            if ((!rule.directoryOnly() || directory) && rule.pattern().matcher(path).matches()) {
                return rule.negated() ? Match.INCLUDED : Match.IGNORED;
            }
        }
        return Match.NONE;
    }

    private static Rule rule(String raw) {
        String line = stripTrailingSpaces(raw);
        if (line.isEmpty() || line.startsWith("#")) {
            return null;
        }
        boolean negated = false;
        if (line.startsWith("!")) {
            negated = true;
            line = line.substring(1);
        } else if (line.startsWith("\\#") || line.startsWith("\\!")) {
            line = line.substring(1);
        }
        boolean directoryOnly = line.endsWith("/");
        if (directoryOnly) {
            line = line.substring(0, line.length() - 1);
        }
        if (line.isEmpty()) {
            return null;
        }
        boolean anchored = line.contains("/");
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        String regex = (anchored ? "" : "(?:.*/)?") + convert(line);
        return new Rule(Pattern.compile(regex), negated, directoryOnly);
    }

    private static String stripTrailingSpaces(String line) {
        int end = line.length();
        while (end > 0 && line.charAt(end - 1) == ' ' && (end < 2 || line.charAt(end - 2) != '\\')) {
            end--;
        }
        return line.substring(0, end).replace("\\ ", " ");
    }

    private static String convert(String glob) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (glob.startsWith("**/", i)) {
                out.append("(?:.*/)?");
                i += 3;
            } else if (glob.startsWith("/**", i) && i + 3 == glob.length()) {
                out.append("/.*");
                i += 3;
            } else if (glob.startsWith("**", i)) {
                out.append(".*");
                i += 2;
            } else if (c == '*') {
                out.append("[^/]*");
                i++;
            } else if (c == '?') {
                out.append("[^/]");
                i++;
            } else if (c == '[' && glob.indexOf(']', i + 1) > 0) {
                int end = glob.indexOf(']', i + 1);
                String inside = glob.substring(i + 1, end);
                boolean not = inside.startsWith("!") || inside.startsWith("^");
                out.append('[').append(not ? "^/" : "").append(escapeClass(not ? inside.substring(1) : inside)).append(']');
                i = end + 1;
            } else if (c == '\\' && i + 1 < glob.length()) {
                out.append(Pattern.quote(String.valueOf(glob.charAt(i + 1))));
                i += 2;
            } else {
                out.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        return out.toString();
    }

    private static String escapeClass(String inside) {
        StringBuilder out = new StringBuilder();
        for (char c : inside.toCharArray()) {
            out.append(c == '-' || Character.isLetterOrDigit(c) ? String.valueOf(c) : "\\" + c);
        }
        return out.toString();
    }
}
