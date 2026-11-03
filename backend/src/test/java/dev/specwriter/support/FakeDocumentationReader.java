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

    @Override
    public int count(DocSource source) {
        Result r = sites.get(source.location());
        return r == null ? 0 : r.pages().size() + r.unread().size();
    }

    @Override
    public Result read(DocSource source) {
        reads.add(source.location());
        Result r = sites.get(source.location());
        return r == null ? new Result(List.of(), List.of(new Unread(source.location(), UnreadReason.UNREACHABLE))) : r;
    }
}
