package dev.specwriter.domain.session;

import java.util.Locale;
import java.util.Set;

/**
 * Langue du besoin (FR-015), par comptage de mots outils. Sans indice suffisant, le français est
 * retenu ; l'utilisateur peut changer la langue avant la génération.
 */
public final class LanguageDetector {

    private static final Set<String> FRENCH = Set.of("le", "la", "les", "un", "une", "des", "du", "de", "et", "est",
            "pour", "dans", "sur", "avec", "que", "qui", "pas", "être", "doit", "doivent", "pouvoir", "quand",
            "lorsque", "elle", "il", "ils", "nous", "vous", "son", "sa", "ses", "au", "aux", "ou", "mais", "si");
    private static final Set<String> ENGLISH = Set.of("the", "a", "an", "and", "is", "are", "for", "in", "on", "with",
            "that", "which", "who", "not", "be", "should", "must", "can", "when", "it", "they", "we", "you", "his",
            "her", "their", "to", "of", "or", "but", "if", "able", "will");

    private LanguageDetector() {
    }

    public static Language detect(String text) {
        int fr = 0;
        int en = 0;
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}']+")) {
            for (String part : word.split("'")) {
                if (FRENCH.contains(part)) {
                    fr++;
                }
                if (ENGLISH.contains(part)) {
                    en++;
                }
            }
        }
        return en > fr ? Language.EN : Language.FR;
    }
}
