package dev.specwriter.domain.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconnaissance des mentions @ (007, research.md R1) : une mention commence par @ en début de
 * texte ou après un blanc ou une ponctuation ouvrante, et s'étend au plus long nom connu suivi
 * d'une fin de mot ; sans nom connu, elle s'arrête au premier blanc et elle est introuvable.
 */
public final class Mentions {

    private static final String OPENING = "([«\"'“‘";
    private static final String CLOSING = ".,;:!?)]»\"'”’";

    private Mentions() {
    }

    /** Mentions d'un texte, dans l'ordre. */
    public static List<Mention> find(String text, MentionField field, List<Mentionable> catalogue) {
        List<Mention> out = new ArrayList<>();
        if (text == null || text.indexOf('@') < 0) {
            return out;
        }
        Map<String, List<Mentionable>> names = names(catalogue);
        String folded = Mentionable.key(text);
        int i = text.indexOf('@');
        while (i >= 0) {
            if (starts(text, i)) {
                Mention m = at(text, folded, i, field, names);
                out.add(m);
                i = text.indexOf('@', m.end());
            } else {
                i = text.indexOf('@', i + 1);
            }
        }
        return out;
    }

    /**
     * Documents désignés : mentions reconnues du besoin, puis des réponses dans l'ordre, puis de
     * l'instruction, sans doublon (FR-011) ; les mentions ambiguës et introuvables n'y sont pas.
     */
    public static List<Mentionable> designated(String need, List<String> answers, String instruction,
            List<Mentionable> catalogue) {
        Set<Mentionable> out = new LinkedHashSet<>();
        add(out, find(need, MentionField.NEED, catalogue));
        for (String answer : answers) {
            add(out, find(answer, MentionField.ANSWER, catalogue));
        }
        add(out, find(instruction, MentionField.INSTRUCTION, catalogue));
        return new ArrayList<>(out);
    }

    /** Mention sans ambiguïté d'un document : son chemin (research.md R2). */
    public static String rewrite(Mentionable document) {
        return "@" + document.path();
    }

    /** Présence d'au moins une mention possible, pour éviter de lire le catalogue sans raison. */
    public static boolean mayContain(String... texts) {
        for (String t : texts) {
            if (t != null && t.indexOf('@') >= 0) {
                return true;
            }
        }
        return false;
    }

    private static void add(Set<Mentionable> out, List<Mention> mentions) {
        mentions.stream().filter(Mention::resolved).forEach(m -> out.add(m.matches().getFirst()));
    }

    private static boolean starts(String text, int at) {
        boolean before = at == 0 || Character.isWhitespace(text.charAt(at - 1)) || OPENING.indexOf(text.charAt(at - 1)) >= 0;
        return before && at + 1 < text.length() && !Character.isWhitespace(text.charAt(at + 1));
    }

    private static Mention at(String text, String folded, int at, MentionField field, Map<String, List<Mentionable>> names) {
        int from = at + 1;
        String best = null;
        for (String name : names.keySet()) {
            int end = from + name.length();
            if ((best == null || name.length() > best.length()) && folded.startsWith(name, from) && boundary(text, end)) {
                best = name;
            }
        }
        if (best != null) {
            int end = from + best.length();
            return new Mention(text.substring(at, end), field, at, end, names.get(best));
        }
        int end = from;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        while (end > from + 1 && CLOSING.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return new Mention(text.substring(at, end), field, at, end, List.of());
    }

    private static boolean boundary(String text, int end) {
        return end == text.length() || Character.isWhitespace(text.charAt(end)) || CLOSING.indexOf(text.charAt(end)) >= 0;
    }

    /** Noms repliés (sans casse ni accents) vers les documents qui les portent, sans doublon. */
    private static Map<String, List<Mentionable>> names(List<Mentionable> catalogue) {
        Map<String, List<Mentionable>> out = new LinkedHashMap<>();
        for (Mentionable m : catalogue) {
            for (String name : m.names()) {
                List<Mentionable> owners = out.computeIfAbsent(Mentionable.key(name), k -> new ArrayList<>());
                if (!owners.contains(m)) {
                    owners.add(m);
                }
            }
        }
        return out;
    }
}
