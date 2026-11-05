package dev.specwriter.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Journal des erreurs : écriture expurgée, rotation et relecture (FR-031, research.md R15, T067). */
class JournalTest {

    @TempDir
    Path dir;

    JournalHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        handler = new JournalHandler(dir);
    }

    @AfterEach
    void tearDown() {
        handler.close();
    }

    void log(Level level, String message, Throwable thrown) {
        ExtLogRecord record = new ExtLogRecord(level, message, ExtLogRecord.FormatStyle.NO_FORMAT, JournalTest.class.getName());
        record.setLoggerName("dev.specwriter.Test");
        record.setThrown(thrown);
        handler.publish(record);
    }

    @Test
    void uneEntreeParLigneMessageCauseEtPileExpurges() throws IOException {
        log(Level.WARNING, "Jira : jeton ghp_abcdefghijklmnop refusé", null);
        log(Level.SEVERE, "Échec", new IllegalStateException("Authorization: Bearer abcdefgh123456",
                new java.io.IOException("ghp_zyxwvutsrqponm")));
        log(Level.INFO, "information non retenue", null);

        List<String> lines = Files.readAllLines(dir.resolve(JournalHandler.FILE), StandardCharsets.UTF_8);
        assertThat(lines).hasSize(2);
        assertThat(String.join("\n", lines)).doesNotContain("ghp_abcdefghijklmnop", "abcdefgh123456", "ghp_zyxwvutsrqponm")
                .contains(RedactingLogFilter.MASK);

        List<JournalEntry> entries = new LogJournal(dir).latest(null);
        assertThat(entries).extracting(JournalEntry::level).containsExactly("ERROR", "WARN");
        JournalEntry error = entries.getFirst();
        assertThat(error.logger()).isEqualTo("dev.specwriter.Test");
        assertThat(error.cause()).startsWith("java.lang.IllegalStateException: " + RedactingLogFilter.MASK)
                .contains("causée par java.io.IOException: " + RedactingLogFilter.MASK);
        // la pile de chaque cause suit celle de l'exception, sous un en-tête « Causée par »
        assertThat(error.stack()).isNotEmpty().first().asString().startsWith("at ");
        assertThat(error.stack()).contains("Causée par : java.io.IOException: " + RedactingLogFilter.MASK)
                .allSatisfy(l -> assertThat(l).matches("(at |Causée par : |… ).*"));
        assertThat(error.at()).isNotNull();
    }

    @Test
    void pileBorneeParExceptionEtAuTotal() throws IOException {
        Throwable chain = deep(new IllegalStateException("racine"), 60);
        for (int i = 0; i < 6; i++) {
            chain = new RuntimeException("niveau " + i, chain);
        }
        log(Level.SEVERE, "Échec", chain);
        JournalEntry entry = new LogJournal(dir).latest(null).getFirst();
        assertThat(entry.stack()).hasSizeLessThanOrEqualTo(JournalFormatter.MAX_STACK_TOTAL)
                .anySatisfy(l -> assertThat(l).matches("… \\d+ lignes? de plus"));
        assertThat(entry.stack().stream().filter(l -> l.startsWith("Causée par")).count()).isGreaterThanOrEqualTo(3);
    }

    /** Exception levée sous {@code depth} appels récursifs : sa pile dépasse MAX_STACK. */
    private static Throwable deep(RuntimeException e, int depth) {
        if (depth == 0) {
            e.fillInStackTrace();
            return e;
        }
        return deep(e, depth - 1);
    }

    @Test
    void filtreParNiveauEtLigneIllisibleIgnoree() throws IOException {
        log(Level.WARNING, "un", null);
        log(Level.SEVERE, "deux", null);
        Files.writeString(dir.resolve(JournalHandler.FILE), "pas du json\n{\"at\":\n", StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);
        log(Level.WARNING, "trois", null);
        LogJournal journal = new LogJournal(dir);
        assertThat(journal.latest("WARN")).extracting(JournalEntry::message).containsExactly("trois", "un");
        assertThat(journal.latest("ERROR")).extracting(JournalEntry::message).containsExactly("deux");
    }

    @Test
    void rotationSurCinqFichiersEtCinqCentsDernieres() throws IOException {
        handler.close();
        handler = new JournalHandler(dir, 4_000);
        for (int i = 0; i < 2_000; i++) {
            log(Level.WARNING, "entrée " + i, null);
        }
        try (var files = Files.list(dir)) {
            assertThat(files.map(f -> f.getFileName().toString()).toList()).containsExactlyInAnyOrder(JournalHandler.FILE,
                    JournalHandler.FILE + ".1", JournalHandler.FILE + ".2", JournalHandler.FILE + ".3",
                    JournalHandler.FILE + ".4");
        }
        List<JournalEntry> entries = new LogJournal(dir).latest(null);
        assertThat(entries).hasSizeLessThanOrEqualTo(LogJournal.MAX_ENTRIES);
        assertThat(entries.getFirst().message()).isEqualTo("entrée 1999");
        assertThat(entries).extracting(JournalEntry::message).isSortedAccordingTo((a, b) ->
                Integer.compare(Integer.parseInt(b.substring(7)), Integer.parseInt(a.substring(7))));
    }

    @Test
    void sansFichierAucuneEntree() {
        assertThat(new LogJournal(dir.resolve("absent")).latest(null)).isEmpty();
    }
}
