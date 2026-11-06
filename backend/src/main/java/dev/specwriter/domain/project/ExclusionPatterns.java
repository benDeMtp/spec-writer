package dev.specwriter.domain.project;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Motifs d'exclusion d'un dossier de documents (006, research.md R4), appliqués au chemin relatif au
 * dossier, sans tenir compte de la casse : {@code *} hors {@code /}, {@code **} sur plusieurs
 * dossiers, {@code ?} un caractère, {@code […]} une classe ({@code [!…]} pour l'exclure). Un motif
 * sans {@code /} s'applique au nom à toute profondeur ; avec {@code /}, au chemin depuis la racine ;
 * terminé par {@code /}, à un dossier et à son contenu.
 */
public final class ExclusionPatterns {

    public static final int MAX_PATTERNS = 50;
    public static final int MAX_LENGTH = 200;
    public static final ExclusionPatterns NONE = new ExclusionPatterns(List.of(), List.of());

    private static final Pattern ABSOLUTE = Pattern.compile("^(/|[A-Za-z]:)");
    private static final Pattern PARENT = Pattern.compile("(^|/)\\.\\.(/|$)");

    private final List<String> patterns;
    private final List<Pattern> compiled;

    private ExclusionPatterns(List<String> patterns, List<Pattern> compiled) {
        this.patterns = patterns;
        this.compiled = compiled;
    }

    /** Motifs saisis, un par ligne ; les lignes vides sont ignorées. */
    public static ExclusionPatterns of(List<String> lines) {
        if (lines == null) {
            return NONE;
        }
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            String p = line == null ? "" : line.strip().replace('\\', '/');
            if (!p.isEmpty()) {
                kept.add(p);
            }
        }
        if (kept.size() > MAX_PATTERNS) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Au plus " + MAX_PATTERNS + " motifs d'exclusion.");
        }
        List<Pattern> compiled = new ArrayList<>();
        for (String p : kept) {
            compiled.add(compile(p));
        }
        return kept.isEmpty() ? NONE : new ExclusionPatterns(List.copyOf(kept), List.copyOf(compiled));
    }

    public List<String> patterns() {
        return patterns;
    }

    public boolean isEmpty() {
        return patterns.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ExclusionPatterns o && o.patterns.equals(patterns);
    }

    @Override
    public int hashCode() {
        return patterns.hashCode();
    }

    @Override
    public String toString() {
        return "ExclusionPatterns" + patterns;
    }

    /** Vrai si un motif s'applique au chemin relatif au dossier. */
    public boolean excludes(String relativePath) {
        String path = relativePath.replace('\\', '/');
        return compiled.stream().anyMatch(p -> p.matcher(path).matches());
    }

    /**
     * Fichiers toujours ignorés, sans motif : fichiers temporaires de bureautique ({@code ~$…},
     * {@code .~lock…}) et éléments cachés (nom commençant par un point, à toute profondeur).
     */
    public static boolean alwaysIgnored(String relativePath) {
        String path = relativePath.replace('\\', '/');
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.startsWith("~$")) {
            return true;
        }
        for (String segment : path.split("/")) {
            if (segment.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private static Pattern compile(String pattern) {
        if (pattern.length() > MAX_LENGTH) {
            throw refused(pattern, "plus de " + MAX_LENGTH + " caractères");
        }
        if (ABSOLUTE.matcher(pattern).find()) {
            throw refused(pattern, "un motif est relatif au dossier, il ne commence ni par / ni par une lettre de lecteur");
        }
        if (PARENT.matcher(pattern).find()) {
            throw refused(pattern, "« .. » ne peut pas sortir du dossier");
        }
        boolean directory = pattern.endsWith("/");
        String body = directory ? pattern.substring(0, pattern.length() - 1) : pattern;
        boolean anchored = body.contains("/");
        StringBuilder regex = new StringBuilder(anchored ? "^" : "^(?:.*/)?");
        regex.append(convert(pattern, body));
        regex.append(directory ? "/.*$" : "$");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static String convert(String pattern, String body) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '*' && i + 1 < body.length() && body.charAt(i + 1) == '*') {
                boolean slashAfter = i + 2 < body.length() && body.charAt(i + 2) == '/';
                out.append(slashAfter ? "(?:.*/)?" : ".*");
                i += slashAfter ? 3 : 2;
            } else if (c == '*') {
                out.append("[^/]*");
                i++;
            } else if (c == '?') {
                out.append("[^/]");
                i++;
            } else if (c == '[') {
                int end = body.indexOf(']', i + 1);
                if (end < 0) {
                    throw refused(pattern, "crochet non fermé");
                }
                String inside = body.substring(i + 1, end);
                boolean negated = inside.startsWith("!");
                out.append('[').append(negated ? "^/" : "").append(classContent(negated ? inside.substring(1) : inside))
                        .append(']');
                i = end + 1;
            } else {
                out.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        return out.toString();
    }

    private static String classContent(String inside) {
        StringBuilder out = new StringBuilder();
        for (char c : inside.toCharArray()) {
            out.append(c == '-' ? "-" : Character.isLetterOrDigit(c) ? String.valueOf(c) : "\\" + c);
        }
        return out.toString();
    }

    private static DomainException refused(String pattern, String reason) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, "Motif refusé « " + pattern + " » : " + reason + ".");
    }
}
