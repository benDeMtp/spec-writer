package dev.specwriter.infrastructure.persistence.conversation;

import dev.specwriter.application.sources.PerimeterView;
import dev.specwriter.domain.port.PerimeterRepository;
import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.ModuleId;
import dev.specwriter.domain.sources.ModuleKind;
import dev.specwriter.domain.sources.ModuleRef;
import dev.specwriter.domain.sources.Occurrence;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterModule;
import dev.specwriter.domain.sources.PerimeterSnapshot;
import dev.specwriter.domain.sources.PerimeterTerms;
import dev.specwriter.domain.sources.Qualification;
import dev.specwriter.domain.sources.Reason;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Analyses de périmètre en base (013, V24, research.md R9). Seuls les extraits bornés des sources
 * sont conservés ; les analyses sont supprimées avec leur conversation.
 */
@ApplicationScoped
public class SqlitePerimeterRepository implements PerimeterRepository {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Inject
    EntityManager em;

    @Override
    @Transactional
    public void save(UUID messageId, PerimeterAnalysis a) {
        String id = messageId + ":" + a.ordinal();
        em.createNativeQuery("delete from perimeter_analysis where id = ?1").setParameter(1, id).executeUpdate();
        em.createNativeQuery("""
                insert into perimeter_analysis (id, message_id, ordinal, terms, rejected_terms, roots, state,
                    incomplete_reason, files_scanned, files_skipped, skipped_examples, duration_ms, architecture_only,
                    unavailable)
                values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)""")
                .setParameter(1, id).setParameter(2, messageId.toString()).setParameter(3, a.ordinal())
                .setParameter(4, JSON.writeValueAsString(a.terms())).setParameter(5, JSON.writeValueAsString(a.rejected()))
                .setParameter(6, JSON.writeValueAsString(a.roots())).setParameter(7, a.state().name())
                .setParameter(8, a.incompleteReason() == null ? null : a.incompleteReason().name())
                .setParameter(9, a.filesScanned()).setParameter(10, a.filesSkipped())
                .setParameter(11, JSON.writeValueAsString(a.skippedExamples())).setParameter(12, a.durationMs())
                .setParameter(13, JSON.writeValueAsString(a.architectureOnly()))
                .setParameter(14, a.unavailable() == null ? null : a.unavailable().name()).executeUpdate();
        int position = 0;
        for (PerimeterModule m : a.modules()) {
            String moduleId = id + ":" + ++position;
            em.createNativeQuery("""
                    insert into perimeter_module (id, analysis_id, position, root_id, root_label, path, name, kind,
                        occurrences, reasons, qualification, justification, is_new, arch_element, arch_status)
                    values (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)""")
                    .setParameter(1, moduleId).setParameter(2, id).setParameter(3, position)
                    .setParameter(4, m.module().rootId().toString()).setParameter(5, label(a, m.module().rootId()))
                    .setParameter(6, m.module().path()).setParameter(7, m.module().name())
                    .setParameter(8, m.module().kind().name()).setParameter(9, m.occurrences())
                    .setParameter(10, JSON.writeValueAsString(m.reasons().stream().map(PerimeterView::reason).toList()))
                    .setParameter(11, m.qualification().name()).setParameter(12, m.justification())
                    .setParameter(13, m.isNew() ? 1 : 0).setParameter(14, m.archElement())
                    .setParameter(15, m.archStatus() == null ? null : m.archStatus().name()).executeUpdate();
            int sample = 0;
            for (Occurrence o : m.samples()) {
                em.createNativeQuery("""
                        insert into perimeter_occurrence (module_id, position, path, line, term, excerpt)
                        values (?1, ?2, ?3, ?4, ?5, ?6)""")
                        .setParameter(1, moduleId).setParameter(2, ++sample).setParameter(3, o.path())
                        .setParameter(4, o.line()).setParameter(5, o.term()).setParameter(6, o.excerpt()).executeUpdate();
            }
        }
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public List<PerimeterAnalysis> list(UUID messageId) {
        List<Object[]> rows = em.createNativeQuery(SELECT + " where a.message_id = ?1 order by a.ordinal")
                .setParameter(1, messageId.toString()).getResultList();
        return rows.stream().map(this::analysis).toList();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Optional<PerimeterAnalysis> last(UUID conversationId) {
        List<Object[]> rows = em.createNativeQuery(SELECT + " join conversation_message m on m.id = a.message_id"
                        + " where m.conversation_id = ?1 and a.unavailable is null order by m.ordinal desc, a.ordinal desc"
                        + " limit 1")
                .setParameter(1, conversationId.toString()).getResultList();
        return rows.stream().findFirst().map(this::analysis);
    }

    @Override
    @Transactional
    public void clear(UUID messageId) {
        em.createNativeQuery("delete from perimeter_analysis where message_id = ?1").setParameter(1, messageId.toString())
                .executeUpdate();
    }

    /** Forme JSON de l'instantané : les raisons à plat, comme dans l'analyse. */
    record SnapshotJson(List<EntryJson> modules, boolean incomplete) {
    }

    record EntryJson(String name, String path, String rootLabel, String qualification, String justification,
            List<PerimeterView.ReasonView> reasons, List<Occurrence> occurrences) {
    }

    @Override
    @Transactional
    public void saveSnapshot(UUID sessionId, PerimeterSnapshot snapshot, Instant takenAt) {
        SnapshotJson json = new SnapshotJson(snapshot.modules().stream().map(e -> new EntryJson(e.name(), e.path(),
                e.rootLabel(), e.qualification().name(), e.justification(),
                e.reasons().stream().map(PerimeterView::reason).toList(), e.occurrences())).toList(), snapshot.incomplete());
        em.createNativeQuery("delete from session_perimeter where session_id = ?1").setParameter(1, sessionId.toString())
                .executeUpdate();
        em.createNativeQuery("insert into session_perimeter (session_id, snapshot, taken_at) values (?1, ?2, ?3)")
                .setParameter(1, sessionId.toString()).setParameter(2, JSON.writeValueAsString(json))
                .setParameter(3, takenAt.toString()).executeUpdate();
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public Optional<PerimeterSnapshot> snapshot(UUID sessionId) {
        List<Object> rows = em.createNativeQuery("select snapshot from session_perimeter where session_id = ?1")
                .setParameter(1, sessionId.toString()).getResultList();
        return rows.stream().findFirst().map(r -> {
            SnapshotJson json = JSON.readValue((String) r, SnapshotJson.class);
            return new PerimeterSnapshot(json.modules().stream().map(e -> new PerimeterSnapshot.Entry(e.name(), e.path(),
                    e.rootLabel(), Qualification.valueOf(e.qualification()), e.justification(),
                    e.reasons().stream().map(SqlitePerimeterRepository::reason).toList(), e.occurrences())).toList(),
                    json.incomplete());
        });
    }

    private static final String SELECT = """
            select a.id, a.ordinal, a.terms, a.rejected_terms, a.roots, a.state, a.incomplete_reason, a.files_scanned,
                a.files_skipped, a.skipped_examples, a.duration_ms, a.architecture_only, a.unavailable
            from perimeter_analysis a""";

    @SuppressWarnings("unchecked")
    private PerimeterAnalysis analysis(Object[] r) {
        String id = (String) r[0];
        List<Object[]> moduleRows = em.createNativeQuery("""
                select id, root_id, path, name, kind, occurrences, reasons, qualification, justification, is_new,
                    arch_element, arch_status from perimeter_module where analysis_id = ?1 order by position""")
                .setParameter(1, id).getResultList();
        List<PerimeterModule> modules = new ArrayList<>();
        for (Object[] m : moduleRows) {
            List<Object[]> samples = em.createNativeQuery("""
                    select path, line, term, excerpt from perimeter_occurrence where module_id = ?1 order by position""")
                    .setParameter(1, m[0]).getResultList();
            Module module = new Module(UUID.fromString((String) m[1]), (String) m[2], (String) m[3],
                    ModuleKind.valueOf((String) m[4]), null);
            List<Reason> reasons = JSON.readValue((String) m[6], new TypeReference<List<PerimeterView.ReasonView>>() {
            }).stream().map(SqlitePerimeterRepository::reason).toList();
            modules.add(new PerimeterModule(module, reasons, ((Number) m[5]).intValue(),
                    samples.stream().map(s -> new Occurrence((String) s[0], ((Number) s[1]).intValue(), (String) s[2],
                            (String) s[3])).toList(),
                    Qualification.valueOf((String) m[7]), (String) m[8], ((Number) m[9]).intValue() == 1, (String) m[10],
                    m[11] == null ? null : PerimeterModule.ArchStatus.valueOf((String) m[11])));
        }
        return new PerimeterAnalysis(((Number) r[1]).intValue(),
                JSON.readValue((String) r[2], new TypeReference<List<String>>() {
                }),
                JSON.readValue((String) r[3], new TypeReference<List<PerimeterTerms.Rejected>>() {
                }),
                JSON.readValue((String) r[4], new TypeReference<List<PerimeterAnalysis.RootStatus>>() {
                }),
                PerimeterAnalysis.State.valueOf((String) r[5]),
                r[6] == null ? null : PerimeterAnalysis.IncompleteReason.valueOf((String) r[6]), ((Number) r[7]).intValue(),
                ((Number) r[8]).intValue(), JSON.readValue((String) r[9], new TypeReference<List<String>>() {
                }), ((Number) r[10]).longValue(),
                modules, JSON.readValue((String) r[11], new TypeReference<List<PerimeterAnalysis.ArchLink>>() {
                }), r[12] == null ? null : PerimeterAnalysis.Unavailable.valueOf((String) r[12]));
    }

    private static Reason reason(PerimeterView.ReasonView v) {
        return switch (v.type()) {
            case "TERM" -> new Reason.Term(v.term(), v.count());
            case "DEPENDENCY" -> new Reason.Dependency(v.chain().stream().map(SqlitePerimeterRepository::ref).toList());
            case "AGGREGATOR" -> new Reason.Aggregator(ref(v.of()));
            default -> new Reason.Architecture(v.element(), v.related(), v.description(), v.technology());
        };
    }

    private static ModuleRef ref(PerimeterView.RefView v) {
        return new ModuleRef(ModuleId.parse(v.id()), v.name());
    }

    private static String label(PerimeterAnalysis a, UUID rootId) {
        return a.roots().stream().filter(r -> r.rootId().equals(rootId)).map(PerimeterAnalysis.RootStatus::label)
                .findFirst().orElse("");
    }
}
