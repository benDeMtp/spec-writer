package dev.specwriter.support;

import dev.specwriter.domain.port.DocumentFiles;
import dev.specwriter.domain.project.DocSource;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Fichiers simulés : dossier déclaré → chemin relatif → contenu. */
public class FakeDocumentFiles implements DocumentFiles {

    public final Map<String, Map<String, String>> folders = new HashMap<>();

    public FakeDocumentFiles put(String folder, String path, String content) {
        folders.computeIfAbsent(folder, f -> new HashMap<>()).put(path, content);
        return this;
    }

    @Override
    public boolean exists(DocSource folder, String path) {
        return folders.getOrDefault(folder.location(), Map.of()).containsKey(path);
    }

    @Override
    public Optional<DocumentFile> read(DocSource folder, String path) {
        return Optional.ofNullable(folders.getOrDefault(folder.location(), Map.of()).get(path))
                .map(c -> new DocumentFile(path, c.getBytes(StandardCharsets.UTF_8)));
    }
}
