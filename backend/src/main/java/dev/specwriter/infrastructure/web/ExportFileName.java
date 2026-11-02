package dev.specwriter.infrastructure.web;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Nom du fichier exporté : {@code <nom-session>-v<n>.md}, sans séparateur de chemin, sans
 * {@code ..} ni caractère interdit sous Windows, borné à 80 caractères avant le suffixe.
 */
final class ExportFileName {

    static final int MAX_BASE = 80;
    private static final Pattern FORBIDDEN = Pattern.compile("[/\\\\:*?\"<>|\\p{Cntrl}]|\\.{2,}");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");

    private ExportFileName() {
    }

    static String of(String sessionName, int version) {
        String base = String.join("-", FORBIDDEN.matcher(sessionName == null ? "" : sessionName).replaceAll(" ")
                .strip().split("\\s+"));
        if (base.length() > MAX_BASE) {
            base = base.substring(0, MAX_BASE);
        }
        base = base.replaceAll("^[-.]+|[-.]+$", "");
        return (base.isEmpty() ? "specification" : base) + "-v" + version + ".md";
    }

    /** Repli {@code filename=} pour les clients sans RFC 6266 : lettres sans accent, le reste en {@code _}. */
    static String ascii(String fileName) {
        String plain = MARKS.matcher(Normalizer.normalize(fileName, Normalizer.Form.NFD)).replaceAll("");
        return plain.replaceAll("[^\\x20-\\x7E]", "_");
    }

    /** {@code Content-Disposition} avec le nom ASCII et le nom UTF-8 encodé (RFC 6266, RFC 5987). */
    static String contentDisposition(String fileName) {
        return "attachment; filename=\"" + ascii(fileName) + "\"; filename*=UTF-8''" + encode(fileName);
    }

    private static String encode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if (c < 0x80 && (Character.isLetterOrDigit(c) || "!#$&+-.^_`|~".indexOf(c) >= 0)) {
                out.append(c);
            } else {
                out.append('%').append(String.format("%02X", b & 0xFF));
            }
        }
        return out.toString();
    }
}
