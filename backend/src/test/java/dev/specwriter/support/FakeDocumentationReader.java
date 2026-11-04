package dev.specwriter.support;

import dev.specwriter.domain.port.DocumentationReader;
import dev.specwriter.domain.project.DocSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Documentation simulée : le résultat préparé pour chaque emplacement de source. */
public class FakeDocumentationReader implements DocumentationReader {

    public final Map<String, Result> sites = new LinkedHashMap<>();
    public final List<String> reads = new ArrayList<>();

    /** Motif renvoyé pour une source sans page ; {@code UNREACHABLE} par défaut. */
    public final Map<String, UnreadReason> problems = new LinkedHashMap<>();

    @Override
    public Availability count(DocSource source) {
        Result r = sites.get(source.location());
        int pages = r == null ? 0 : r.pages().size() + r.unread().size();
        return pages > 0 ? Availability.of(pages)
                : new Availability(0, problems.getOrDefault(source.location(), UnreadReason.UNREACHABLE));
    }

    /** Appelé avant chaque page, après l'écouteur : un test peut y demander l'interruption. */
    public Runnable onPage = () -> { };

    @Override
    public Result read(DocSource source, Listener listener) {
        reads.add(source.location());
        Result r = sites.get(source.location());
        if (r == null) {
            return new Result(List.of(), List.of(new Unread(source.location(), UnreadReason.UNREACHABLE)));
        }
        int total = r.pages().size() + r.unread().size();
        int done = 0;
        List<Page> pages = new ArrayList<>();
        List<Unread> unread = new ArrayList<>();
        for (Page p : r.pages()) {
            if (listener.cancelled()) {
                return new Result(pages, unread);
            }
            listener.reading(p.ref(), done++, total);
            onPage.run();
            pages.add(p);
        }
        for (Unread u : r.unread()) {
            if (listener.cancelled()) {
                return new Result(pages, unread);
            }
            listener.reading(u.location(), done++, total);
            onPage.run();
            unread.add(u);
        }
        return new Result(pages, unread);
    }
}
