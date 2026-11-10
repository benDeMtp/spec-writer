package dev.specwriter.domain.conversation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Citations {@code [n]} d'une réponse (010, FR-003) : un numéro qui ne désigne aucune source du tour
 * est retiré du texte ; les sources citées sont rendues dans l'ordre de leur première citation.
 */
public final class Citations {

    /** {@code [n]} qui n'ouvre pas un lien Markdown. */
    private static final Pattern CITATION = Pattern.compile(" ?\\[(\\d{1,3})](?!\\()");

    public record Resolved(String text, List<CitedSource> cited) {
    }

    private Citations() {
    }

    public static Resolved resolve(String text, List<TurnSource> sources) {
        Map<Integer, TurnSource> byNumber = new LinkedHashMap<>();
        sources.forEach(s -> byNumber.put(s.n(), s));
        Map<Integer, CitedSource> cited = new LinkedHashMap<>();
        Matcher m = CITATION.matcher(text == null ? "" : text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            TurnSource source = byNumber.get(n);
            if (source == null) {
                m.appendReplacement(out, "");
            } else {
                cited.putIfAbsent(n, source.cited());
                m.appendReplacement(out, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(out);
        return new Resolved(out.toString(), List.copyOf(cited.values()));
    }

    /**
     * Réponse ancienne transmise dans l'historique : chaque {@code [n]} devient le titre de sa
     * source, la numérotation d'un tour passé ne valant plus au tour suivant ; un numéro sans source
     * est retiré.
     */
    public static String inline(String text, List<CitedSource> cited) {
        Map<Integer, CitedSource> byNumber = new LinkedHashMap<>();
        cited.forEach(c -> byNumber.put(c.n(), c));
        Matcher m = CITATION.matcher(text == null ? "" : text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            CitedSource source = byNumber.get(Integer.parseInt(m.group(1)));
            m.appendReplacement(out, source == null ? "" : Matcher.quoteReplacement(" (« " + source.title() + " »)"));
        }
        m.appendTail(out);
        return out.toString();
    }
}
