package dev.specwriter.support;

import dev.specwriter.domain.port.DocumentFolder;
import dev.specwriter.domain.port.DocumentationReader.Page;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.project.DocSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Dossiers de documents simulés (006) : fichiers préparés par emplacement, lectures tracées. */
public class FakeDocumentFolder implements DocumentFolder {

    /** Fichier simulé : état et résultat de sa lecture. */
    public record Doc(FileEntry entry, Reading reading) {
    }

    public final Map<String, Map<String, Doc>> folders = new LinkedHashMap<>();
    public final Map<String, List<String>> ignored = new LinkedHashMap<>();
    public final Map<String, Boolean> limitReached = new LinkedHashMap<>();
    /** Chemins lus, dans l'ordre, tous dossiers confondus. */
    public final List<String> reads = new ArrayList<>();
    public final Map<String, RuntimeException> broken = new LinkedHashMap<>();

    public void put(String location, String path, long size, Instant modified, Reading reading) {
        folders.computeIfAbsent(location, l -> new LinkedHashMap<>())
                .put(path, new Doc(new FileEntry(path, size, modified), reading));
    }

    public void remove(String location, String path) {
        folders.getOrDefault(location, Map.of()).remove(path);
    }

    @Override
    public Scan scan(DocSource source) {
        if (broken.containsKey(source.location())) {
            throw broken.get(source.location());
        }
        Map<String, Doc> folder = folders.get(source.location());
        if (folder == null) {
            return new Scan(List.of(), List.of(), List.of(), false, UnreadReason.FOLDER_NOT_FOUND);
        }
        List<FileEntry> retained = new ArrayList<>();
        List<String> excluded = new ArrayList<>();
        for (Doc doc : folder.values()) {
            if (source.exclusions().excludes(doc.entry().path())) {
                excluded.add(doc.entry().path());
            } else {
                retained.add(doc.entry());
            }
        }
        retained.sort(Comparator.comparing(FileEntry::path));
        return new Scan(retained, excluded, ignored.getOrDefault(source.location(), List.of()),
                limitReached.getOrDefault(source.location(), false), null);
    }

    @Override
    public Reading read(DocSource source, String path) {
        reads.add(path);
        Doc doc = folders.getOrDefault(source.location(), Map.of()).get(path);
        return doc == null ? Reading.failed(UnreadReason.UNAVAILABLE) : doc.reading();
    }

    /** Lecture réussie d'une page déjà découpée. */
    public static Reading page(Page page) {
        return Reading.of(page);
    }
}
