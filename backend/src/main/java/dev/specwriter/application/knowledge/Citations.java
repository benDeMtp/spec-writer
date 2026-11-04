package dev.specwriter.application.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Réparation des citations produites par le LLM, sans le rappeler (FR-013, FR-013g) : une référence
 * écrite autrement (chemin relatif, ancre, casse, ponctuation) est ramenée à la source lue qu'elle
 * désigne sans ambiguïté ; une affirmation dont aucune source n'est connue est retirée, car elle
 * n'est pas vérifiable.
 */
public final class Citations {

    public static final Pattern CITATION = Pattern.compile("\\[source\\s*:\\s*([^\\]]+)]", Pattern.CASE_INSENSITIVE);

    /** Fiche réparée : références renommées, inconnues, lignes retirées et citations valides restantes. */
    public record Repaired(String markdown, Map<String, String> renamed, Set<String> unknown, int removedLines, int valid) {

        public boolean changed() {
            return !renamed.isEmpty() || !unknown.isEmpty();
        }
    }

    /** Résolution d'une référence citée vers une source connue, ou {@code null}. */
    public static final class Resolver {
        private final Set<String> known;

        private Resolver(Set<String> known) {
            this.known = known;
        }

        public String resolve(String raw) {
            String c = trimmed(raw);
            if (c.isEmpty()) {
                return null;
            }
            if (known.contains(c)) {
                return c;
            }
            String bare = withoutAnchor(c);
            if (known.contains(bare)) {
                return bare;
            }
            String lower = bare.toLowerCase(Locale.ROOT);
            String exact = unique(known.stream().filter(k -> k.toLowerCase(Locale.ROOT).equals(lower)).toList());
            if (exact != null) {
                return exact;
            }
            String tail = tail(lower);
            // un nom de page, pas un mot : il faut au moins un point ou une barre
            if (tail.isEmpty() || tail.indexOf('.') < 0 && tail.indexOf('/') < 0) {
                return null;
            }
            return unique(known.stream().filter(k -> {
                String kt = tail(k.toLowerCase(Locale.ROOT));
                return !kt.isEmpty() && (kt.equals(tail) || kt.endsWith("/" + tail) || tail.endsWith("/" + kt));
            }).toList());
        }

        private static String unique(List<String> candidates) {
            return candidates.size() == 1 ? candidates.getFirst() : null;
        }
    }

    private Citations() {
    }

    public static Resolver resolver(Set<String> known) {
        return new Resolver(known);
    }

    /** Sans les signes qui entourent souvent une référence : chevrons, guillemets, ponctuation finale. */
    private static String trimmed(String raw) {
        String c = raw == null ? "" : raw.strip();
        boolean again = true;
        while (again && !c.isEmpty()) {
            again = false;
            if (c.length() > 1 && "<`\"'«(".indexOf(c.charAt(0)) >= 0) {
                c = c.substring(1).strip();
                again = true;
            }
            if (!c.isEmpty() && ">`\"'».,;:)".indexOf(c.charAt(c.length() - 1)) >= 0) {
                c = c.substring(0, c.length() - 1).strip();
                again = true;
            }
        }
        return c;
    }

    private static String withoutAnchor(String ref) {
        int cut = ref.length();
        for (char sep : new char[] {'#', '?'}) {
            int i = ref.indexOf(sep);
            if (i > 0) {
                cut = Math.min(cut, i);
            }
        }
        return ref.substring(0, cut);
    }

    /** Le chemin d'une adresse, sans schéma, hôte ni barre initiale. */
    private static String tail(String ref) {
        String t = ref;
        int scheme = t.indexOf("://");
        if (scheme >= 0) {
            int path = t.indexOf('/', scheme + 3);
            t = path < 0 ? "" : t.substring(path);
        }
        while (t.startsWith("/")) {
            t = t.substring(1);
        }
        return t;
    }

    /**
     * Réécrit chaque citation avec les références résolues. Un groupe sans référence connue disparaît ;
     * une ligne qui citait des sources et n'en garde aucune est retirée.
     */
    public static Repaired repair(String markdown, Resolver resolver) {
        Map<String, String> renamed = new LinkedHashMap<>();
        Set<String> unknown = new LinkedHashSet<>();
        List<String> kept = new ArrayList<>();
        int removed = 0;
        int valid = 0;
        for (String line : markdown.split("\n", -1)) {
            Matcher m = CITATION.matcher(line);
            StringBuilder out = new StringBuilder();
            boolean cites = false;
            int lineValid = 0;
            while (m.find()) {
                cites = true;
                Set<String> refs = new LinkedHashSet<>();
                for (String raw : m.group(1).split(",")) {
                    if (raw.isBlank()) {
                        continue;
                    }
                    String ref = resolver.resolve(raw);
                    if (ref == null) {
                        unknown.add(raw.strip());
                    } else {
                        if (!ref.equals(raw.strip())) {
                            renamed.put(raw.strip(), ref);
                        }
                        refs.add(ref);
                    }
                }
                lineValid += refs.size();
                m.appendReplacement(out, Matcher.quoteReplacement(refs.isEmpty() ? "" : "[source : " + String.join(", ", refs) + "]"));
            }
            m.appendTail(out);
            if (cites && lineValid == 0) {
                removed++;
                continue;
            }
            valid += lineValid;
            kept.add(cites ? out.toString().replaceAll(" {2,}", " ").stripTrailing() : line);
        }
        String result = String.join("\n", kept);
        return new Repaired(unknown.isEmpty() && renamed.isEmpty() ? markdown : result,
                Collections.unmodifiableMap(renamed), Collections.unmodifiableSet(unknown), removed, valid);
    }
}
