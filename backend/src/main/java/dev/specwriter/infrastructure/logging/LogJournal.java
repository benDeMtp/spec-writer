package dev.specwriter.infrastructure.logging;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Relecture du journal des erreurs : du fichier courant aux sauvegardes, les plus récentes
 * d'abord, 500 entrées au plus ; une ligne illisible est ignorée (FR-031, research.md R15).
 */
public final class LogJournal {

    public static final int MAX_ENTRIES = 500;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Path dir;

    public LogJournal(Path dir) {
        this.dir = dir;
    }

    /** @param level {@code WARN} ou {@code ERROR}, ou {@code null} pour les deux */
    public List<JournalEntry> latest(String level) {
        List<JournalEntry> out = new ArrayList<>();
        for (int i = 0; i <= JournalHandler.BACKUPS && out.size() < MAX_ENTRIES; i++) {
            Path file = dir.resolve(i == 0 ? JournalHandler.FILE : JournalHandler.FILE + "." + i);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            List<String> lines = read(file);
            for (int l = lines.size() - 1; l >= 0 && out.size() < MAX_ENTRIES; l--) {
                JournalEntry e = parse(lines.get(l));
                if (e != null && (level == null || level.equals(e.level()))) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    private static List<String> read(Path file) {
        try {
            // décodage tolérant : une ligne tronquée par un arrêt brutal ne bloque pas la lecture
            return List.of(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).split("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JournalEntry parse(String line) {
        if (line.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(line, JournalEntry.class);
        } catch (JacksonException e) {
            return null;
        }
    }
}
