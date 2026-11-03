package dev.specwriter.infrastructure.logging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.jboss.logmanager.ExtLogRecord;
import tools.jackson.databind.json.JsonMapper;

/**
 * Une entrée JSON par ligne, dont le message, la cause et la pile sont expurgés par
 * {@link RedactingLogFilter#redact} : le filtre de la console ne touche pas aux exceptions
 * (research.md R15).
 */
final class JournalFormatter extends Formatter {

    static final int MAX_STACK = 20;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public String format(LogRecord record) {
        String message = record instanceof ExtLogRecord ext ? ext.getFormattedMessage() : formatMessage(record);
        JournalEntry entry = new JournalEntry(Instant.ofEpochMilli(record.getMillis()), level(record.getLevel()),
                record.getLoggerName(), redact(message), cause(record.getThrown()), stack(record.getThrown()));
        return JSON.writeValueAsString(entry) + "\n";
    }

    static String level(Level level) {
        return level.intValue() >= Level.SEVERE.intValue() ? "ERROR" : "WARN";
    }

    /** Classe et message de chaque cause, de l'exception à la cause première. */
    private static String cause(Throwable thrown) {
        if (thrown == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Throwable t = thrown; t != null && parts.size() < 10; t = t.getCause() == t ? null : t.getCause()) {
            parts.add(t.getClass().getName() + (t.getMessage() == null ? "" : ": " + t.getMessage()));
        }
        return redact(String.join(" ; causée par ", parts));
    }

    private static List<String> stack(Throwable thrown) {
        if (thrown == null) {
            return List.of();
        }
        return java.util.Arrays.stream(thrown.getStackTrace()).limit(MAX_STACK).map(e -> redact("at " + e)).toList();
    }

    private static String redact(String text) {
        return text == null ? null : RedactingLogFilter.redact(text);
    }
}
