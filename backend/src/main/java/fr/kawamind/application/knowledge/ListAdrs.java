package fr.kawamind.application.knowledge;

import fr.kawamind.application.knowledge.AdrList.AdrSummary;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.knowledge.AdrNumber;
import fr.kawamind.domain.knowledge.AdrStatus;
import fr.kawamind.domain.knowledge.KnowledgeItem;
import fr.kawamind.domain.knowledge.KnowledgeKind;
import fr.kawamind.domain.port.DocSourceRepository;
import fr.kawamind.domain.port.DocumentFiles;
import fr.kawamind.domain.port.KnowledgeIndex;
import fr.kawamind.domain.port.ProjectRepository;
import fr.kawamind.domain.project.DocSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ADR du projet pour la page des ADR (feature 004, research.md R2) : un ADR découpé en morceaux est
 * une seule ligne ; tri par numéro puis titre, sans numéro à la fin ; doublons de numéro et
 * successeurs introuvables signalés. Rien n'est envoyé hors du poste ni journalisé.
 */
public class ListAdrs {

    private static final Set<AdrStatus> NOT_SENT = Set.of(AdrStatus.SUPERSEDED, AdrStatus.DEPRECATED);
    private static final Comparator<AdrSummary> ORDER = Comparator
            .comparing(AdrSummary::number, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(AdrSummary::title);

    private final ProjectRepository projects;
    private final KnowledgeIndex index;
    private final DocSourceRepository docSources;
    private final DocumentFiles files;
    private final Indexations indexations;

    public ListAdrs(ProjectRepository projects, KnowledgeIndex index, DocSourceRepository docSources,
            DocumentFiles files, Indexations indexations) {
        this.projects = projects;
        this.index = index;
        this.docSources = docSources;
        this.files = files;
        this.indexations = indexations;
    }

    public AdrList run(UUID projectId) {
        if (projects.find(projectId).isEmpty()) {
            throw new DomainException(ErrorCode.PROJECT_NOT_FOUND);
        }
        // un ADR par référence : son morceau 0 porte le titre, le statut et le successeur
        Map<String, KnowledgeItem> adrs = index.all(projectId, KnowledgeKind.ADR).stream()
                .sorted(Comparator.comparingInt(KnowledgeItem::chunk))
                .collect(Collectors.toMap(KnowledgeItem::ref, Function.identity(), (first, next) -> first,
                        LinkedHashMap::new));
        Map<KnowledgeItem, OptionalInt> numbers = new LinkedHashMap<>();
        adrs.values().forEach(a -> numbers.put(a, AdrNumber.of(a.ref(), a.title())));
        Map<Integer, Long> uses = numbers.values().stream().filter(OptionalInt::isPresent)
                .collect(Collectors.groupingBy(OptionalInt::getAsInt, Collectors.counting()));
        List<DocSource> folders = docSources.list(projectId).stream().filter(s -> s.kind() == DocSource.Kind.FOLDER)
                .toList();

        List<AdrSummary> summaries = new ArrayList<>();
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        numbers.forEach((adr, number) -> {
            AdrStatus status = adr.adrStatus() == null ? AdrStatus.UNKNOWN : adr.adrStatus();
            byStatus.merge(status.name(), 1, Integer::sum);
            OptionalInt successor = AdrNumber.ofCited(adr.supersededBy());
            summaries.add(new AdrSummary(number.isPresent() ? number.getAsInt() : null, adr.title(), status.name(),
                    adr.supersededBy(), successor.isPresent() && uses.containsKey(successor.getAsInt()),
                    number.isPresent() && uses.get(number.getAsInt()) > 1, !NOT_SENT.contains(status),
                    url(projectId, adr.ref(), folders)));
        });
        summaries.sort(ORDER);
        boolean indexing = indexations.current(projectId)
                .filter(s -> s.status() == Indexations.Status.RUNNING).isPresent();
        return new AdrList(List.copyOf(summaries), byStatus, indexing);
    }

    /** Adresse du site, ou fichier d'un dossier déclaré servi par l'outil (research.md R3) ; nul sinon. */
    private String url(UUID projectId, String ref, List<DocSource> folders) {
        if (ref.toLowerCase(Locale.ROOT).startsWith("http")) {
            return ref;
        }
        if (ref.startsWith("docs:")) {
            // dossier de documents (006) : le fichier n'est jamais servi par l'outil
            return null;
        }
        String path = ref.replaceAll("[?#].*$", "");
        return folders.stream().anyMatch(f -> files.exists(f, path))
                ? "/api/projects/" + projectId + "/doc-files/" + path
                : null;
    }
}
