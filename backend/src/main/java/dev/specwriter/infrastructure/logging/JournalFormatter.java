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

    /** Lignes de pile gardées par exception, puis pour l'entrée entière. */
    static final int MAX_STACK = 20;
    static final int MAX_STACK_TOTAL = 80;
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
            parts.add(describe(t));
        }
        return redact(String.join(" ; causée par ", parts));
    }

    private static String describe(Throwable t) {
        if (t instanceof SafeThrowable) {
            return t.getMessage();
        }
        return t.getClass().getName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }

    /**
     * Pile de l'exception, puis celle de chaque cause sous un en-tête « Causée par » : la cause
     * première, souvent levée par le SDK ou le client HTTP, dit où l'échec s'est produit.
     */
    private static List<String> stack(Throwable thrown) {
        List<String> lines = new ArrayList<>();
        int depth = 0;
        for (Throwable t = thrown; t != null && depth < 10 && lines.size() < MAX_STACK_TOTAL;
                t = t.getCause() == t ? null : t.getCause(), depth++) {
            if (t != thrown) {
                lines.add("Causée par : " + describe(t));
            }
            StackTraceElement[] frames = t.getStackTrace();
            int kept = Math.max(0, Math.min(Math.min(frames.length, MAX_STACK), MAX_STACK_TOTAL - 1 - lines.size()));
            for (int i = 0; i < kept; i++) {
                lines.add("at " + frames[i]);
            }
            if (kept < frames.length && lines.size() < MAX_STACK_TOTAL) {
                int more = frames.length - kept;
                lines.add("… " + more + (more > 1 ? " lignes de plus" : " ligne de plus"));
            }
        }
        return lines.stream().limit(MAX_STACK_TOTAL).map(JournalFormatter::redact).toList();
    }

    private static String redact(String text) {
        return text == null ? null : RedactingLogFilter.redact(text);
    }
}
