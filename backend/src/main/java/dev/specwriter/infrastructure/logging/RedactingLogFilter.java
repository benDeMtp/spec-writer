package dev.specwriter.infrastructure.logging;

import io.quarkus.logging.LoggingFilter;
import java.util.logging.Filter;
import org.jboss.logmanager.ExtLogRecord;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

/**
 * Masque les jetons dans tous les journaux (FR-034), en dernier rempart : le code ne doit déjà
 * jamais journaliser de jeton ni de contenu saisi (FR-025).
 */
@LoggingFilter(name = "redacting")
public final class RedactingLogFilter implements Filter {

    static final String MASK = "[MASQUÉ]";

    private static final Pattern SECRETS = Pattern.compile(
            "\\b(?:gh[opusr]_[A-Za-z0-9_]{4,}|github_pat_[A-Za-z0-9_]{10,})"
                    + "|(?i:authorization)\\s*[:=]\\s*(?:(?i:bearer|basic|token)\\s+)?\\S+"
                    + "|(?i:bearer)\\s+[A-Za-z0-9._~+/=-]{4,}"
                    + "|(?i:launch=)[A-Za-z0-9_-]{8,}");

    @Override
    public boolean isLoggable(LogRecord record) {
        if (record instanceof ExtLogRecord ext) {
            // Message formaté puis masqué ; le style NO_FORMAT évite toute reformatage ultérieur.
            String formatted = ext.getFormattedMessage();
            if (formatted != null) {
                String redacted = redact(formatted);
                if (!redacted.equals(formatted) || ext.getParameters() != null) {
                    ext.setParameters(null);
                    ext.setMessage(redacted, ExtLogRecord.FormatStyle.NO_FORMAT);
                }
            }
            return true;
        }
        if (record.getMessage() != null) {
            record.setMessage(redact(record.getMessage()));
        }
        Object[] params = record.getParameters();
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof CharSequence s) {
                    params[i] = redact(s.toString());
                }
            }
        }
        return true;
    }

    public static String redact(String text) {
        return SECRETS.matcher(text).replaceAll(MASK);
    }
}
