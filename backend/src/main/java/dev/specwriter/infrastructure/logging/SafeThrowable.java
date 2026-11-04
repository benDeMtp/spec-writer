package dev.specwriter.infrastructure.logging;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Copie journalisable d'une chaîne d'exceptions : la classe et la pile de chaque cause, sans son
 * message, qui peut citer la requête envoyée (FR-025 de 001). Seuls des marqueurs techniques
 * reconnus en sont repris : code HTTP, erreur réseau ou de certificat, code de sortie, délai.
 */
public final class SafeThrowable extends RuntimeException {

    private static final long serialVersionUID = 1L;

    static final int MAX_DEPTH = 8;

    private static final Pattern HTTP = Pattern.compile("(?i)\\b(?:http|status(?: code)?)\\s*:?\\s*([1-5]\\d\\d)\\b");
    private static final Pattern EXIT = Pattern.compile("(?i)\\bexit(?:ed with)? code:?\\s*(-?\\d{1,3})\\b");
    private static final Pattern CODE = Pattern.compile("\\b[A-Z][A-Z0-9_]{4,40}\\b");
    private static final Set<String> CODES = Set.of("ECONNREFUSED", "ECONNRESET", "ETIMEDOUT", "ENOTFOUND", "EAI_AGAIN",
            "EPIPE", "EHOSTUNREACH", "ENETUNREACH", "ECONNABORTED", "UNABLE_TO_GET_ISSUER_CERT_LOCALLY",
            "UNABLE_TO_VERIFY_LEAF_SIGNATURE", "SELF_SIGNED_CERT_IN_CHAIN", "DEPTH_ZERO_SELF_SIGNED_CERT",
            "CERT_HAS_EXPIRED", "ERR_TLS_CERT_ALTNAME_INVALID", "CERT_UNTRUSTED");
    /** Indices en clair : le fragment cherché et le marqueur écrit. */
    private static final List<String[]> HINTS = List.of(
            new String[] {"certificate", "certificat"},
            new String[] {"proxy", "proxy"},
            new String[] {"token count", "limite de jetons"},
            new String[] {"context_length_exceeded", "limite de jetons"},
            new String[] {"too many tokens", "limite de jetons"},
            new String[] {"timed out", "délai dépassé"},
            new String[] {"timeout", "délai dépassé"},
            new String[] {"socket hang up", "connexion coupée"},
            new String[] {"connection closed", "connexion coupée"},
            new String[] {"stream closed", "connexion coupée"});

    private SafeThrowable(String message, SafeThrowable cause) {
        super(message, cause, false, true);
    }

    public static SafeThrowable of(Throwable error) {
        return copy(error, 1);
    }

    private static SafeThrowable copy(Throwable error, int depth) {
        String markers = String.join(", ", markers(error.getMessage()));
        Throwable cause = error.getCause();
        SafeThrowable safe = new SafeThrowable(error.getClass().getName() + (markers.isEmpty() ? "" : " [" + markers + "]"),
                cause != null && cause != error && depth < MAX_DEPTH ? copy(cause, depth + 1) : null);
        safe.setStackTrace(error.getStackTrace());
        return safe;
    }

    static Set<String> markers(String message) {
        Set<String> found = new LinkedHashSet<>();
        if (message == null) {
            return found;
        }
        Matcher http = HTTP.matcher(message);
        if (http.find()) {
            found.add("HTTP " + http.group(1));
        }
        Matcher code = CODE.matcher(message);
        while (code.find()) {
            if (CODES.contains(code.group())) {
                found.add(code.group());
            }
        }
        Matcher exit = EXIT.matcher(message);
        if (exit.find()) {
            found.add("code de sortie " + exit.group(1));
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String[] hint : HINTS) {
            if (lower.contains(hint[0])) {
                found.add(hint[1]);
            }
        }
        return found;
    }

    /** La classe d'origine, et non celle de la copie, pour la console comme pour le journal. */
    @Override
    public String toString() {
        return getMessage();
    }
}
