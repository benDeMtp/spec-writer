package dev.specwriter.application.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.port.SourceTree;
import dev.specwriter.domain.project.ExclusionPatterns;
import dev.specwriter.domain.session.Language;
import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.ModuleDescriptor;
import dev.specwriter.domain.sources.ModuleId;
import dev.specwriter.domain.sources.ModuleKind;
import dev.specwriter.domain.sources.ModuleRef;
import dev.specwriter.domain.sources.ModuleQualification;
import dev.specwriter.domain.sources.Occurrence;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterModule;
import dev.specwriter.domain.sources.PerimeterTerms;
import dev.specwriter.domain.sources.Qualification;
import dev.specwriter.domain.sources.Reason;
import dev.specwriter.domain.sources.SourceRoot;
import dev.specwriter.support.FixedClock;
import dev.specwriter.support.InMemorySourceRoots;
import dev.specwriter.support.ScriptedConversationLlm;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class AnalyzePerimeterTest {

    static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000101");
    static final UUID ROOT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    final InMemorySourceRoots roots = new InMemorySourceRoots();
    final ScriptedConversationLlm llm = new ScriptedConversationLlm();
    final List<PerimeterTerms> searched = new ArrayList<>();
    SourceTree.Scan scan = scan(List.of(), null);
    final SourceTree tree = new SourceTree() {
        @Override
        public Preview preview(SourceRoot root, Limits limits) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scan search(List<SourceRoot> declared, PerimeterTerms terms, Limits limits, BooleanSupplier cancelled) {
            searched.add(terms);
            return scan;
        }
    };
    final AnalyzePerimeter analyze = new AnalyzePerimeter(roots, tree, llm, new FixedClock());

    static Module module(String path, String name) {
        return new Module(ROOT, path, name, ModuleKind.MAVEN, null);
    }

    static SourceTree.ModuleHit hit(String path, Map<String, Integer> counts) {
        return new SourceTree.ModuleHit(module(path, path.substring(path.lastIndexOf('/') + 1)), counts,
                List.of(new Occurrence(path + "/A.java", 3, counts.keySet().iterator().next(), "ligne")));
    }

    static SourceTree.Scan scan(List<SourceTree.ModuleHit> hits, PerimeterAnalysis.IncompleteReason incomplete) {
        return new SourceTree.Scan(hits, hits.stream().map(SourceTree.ModuleHit::module).toList(), Map.of(),
                List.of(new PerimeterAnalysis.RootStatus(ROOT, "rv", PerimeterAnalysis.RootState.SCANNED)), 40, 2,
                List.of(), incomplete);
    }

    void declareRoot() {
        roots.save(new SourceRoot(ROOT, PROJECT, "/src/rv", ExclusionPatterns.NONE, Instant.EPOCH));
    }

    @Test
    void sansRacineLAnalyseEstIndisponibleSansRienParcourir() {
        PerimeterAnalysis analysis = analyze.analyze(PROJECT, List.of("ContractStatus"), null, 1, () -> false);
        assertThat(analysis.unavailable()).isEqualTo(PerimeterAnalysis.Unavailable.NO_ROOTS);
        assertThat(searched).isEmpty();
        assertThat(analyze.available(PROJECT)).isFalse();
    }

    @Test
    void lesModulesTrouvesPortentLeursTermesEtSontTries() {
        declareRoot();
        scan = scan(List.of(hit("modules/b", Map.of("CONTRACT_STATUS", 1)),
                hit("libs/a", new java.util.LinkedHashMap<>(Map.of("ContractStatus", 7)))), null);
        PerimeterAnalysis analysis = analyze.analyze(PROJECT, List.of("ContractStatus", "CONTRACT_STATUS", "x"), null, 2,
                () -> false);
        assertThat(searched.getFirst().values()).containsExactly("ContractStatus", "CONTRACT_STATUS");
        assertThat(analysis.ordinal()).isEqualTo(2);
        assertThat(analysis.rejected()).hasSize(1);
        assertThat(analysis.modules()).extracting(m -> m.module().path()).containsExactly("libs/a", "modules/b");
        assertThat(analysis.modules().getFirst().reasons()).containsExactly(new Reason.Term("ContractStatus", 7));
        assertThat(analysis.modules().getFirst().occurrences()).isEqualTo(7);
        assertThat(analysis.modules()).allMatch(m -> m.qualification() == Qualification.UNQUALIFIED);
        assertThat(analysis.filesScanned()).isEqualTo(40);
        assertThat(analysis.state()).isEqualTo(PerimeterAnalysis.State.COMPLETE);
    }

    @Test
    void uneAnalyseArreteeEstIncompleteEtLesNouveauxModulesSontSignales() {
        declareRoot();
        scan = scan(List.of(hit("libs/a", Map.of("ContractStatus", 1))), null);
        PerimeterAnalysis first = analyze.analyze(PROJECT, List.of("ContractStatus"), null, 1, () -> false);
        scan = scan(List.of(hit("libs/a", Map.of("ContractStatus", 1)), hit("modules/batch", Map.of("contract_suspended", 1))),
                PerimeterAnalysis.IncompleteReason.MAX_DURATION);
        PerimeterAnalysis second = analyze.analyze(PROJECT, List.of("ContractStatus", "contract_suspended"), first, 1,
                () -> false);
        assertThat(second.state()).isEqualTo(PerimeterAnalysis.State.INCOMPLETE);
        assertThat(second.incompleteReason()).isEqualTo(PerimeterAnalysis.IncompleteReason.MAX_DURATION);
        assertThat(second.modules()).extracting(PerimeterModule::isNew).containsExactly(false, true);
    }

    @Test
    void sansTermeValideLAnalyseEstRefusee() {
        declareRoot();
        assertThatThrownBy(() -> analyze.analyze(PROJECT, List.of("x"), null, 1, () -> false))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void laQualificationDeLAgentEstAppliqueeSansRetirerDeModule() {
        declareRoot();
        scan = scan(List.of(hit("libs/a", Map.of("ContractStatus", 3)), hit("modules/b", Map.of("ContractStatus", 1))), null);
        PerimeterAnalysis analysis = analyze.analyze(PROJECT, List.of("ContractStatus"), null, 1, () -> false);
        llm.qualifications.add(List.of(new ModuleQualification(ROOT + "/libs/a", Qualification.IMPACTED, "Définit.")));
        AnalyzePerimeter.Qualified qualified = analyze.qualify(analysis, Language.FR, List.of(), "Quels modules ?");
        assertThat(qualified.failed()).isFalse();
        assertThat(qualified.analysis().modules()).extracting(PerimeterModule::qualification)
                .containsExactly(Qualification.IMPACTED, Qualification.UNQUALIFIED);
        assertThat(llm.qualifyRequests.getFirst().question()).isEqualTo("Quels modules ?");
    }

    @Test
    void uneQualificationEnEchecLaisseLAnalyseAfficheeSansQualification() {
        declareRoot();
        scan = scan(List.of(hit("libs/a", Map.of("ContractStatus", 3))), null);
        PerimeterAnalysis analysis = analyze.analyze(PROJECT, List.of("ContractStatus"), null, 1, () -> false);
        llm.qualifyFailure = new DomainException(ErrorCode.LLM_UNAVAILABLE);
        AnalyzePerimeter.Qualified qualified = analyze.qualify(analysis, Language.FR, List.of(), "q");
        assertThat(qualified.failed()).isTrue();
        assertThat(qualified.analysis()).isEqualTo(analysis);
    }

    @Test
    void sansModuleRienNEstDemandeALAgent() {
        declareRoot();
        PerimeterAnalysis analysis = analyze.analyze(PROJECT, List.of("ContractStatus"), null, 1, () -> false);
        assertThat(analyze.qualify(analysis, Language.FR, List.of(), "q").analysis().modules()).isEmpty();
        assertThat(llm.qualifyRequests).isEmpty();
    }

    static ModuleDescriptor pom(String artifact, String... deps) {
        return new ModuleDescriptor(artifact, "fr.demo:" + artifact, null, Set.of(deps), Set.of(), List.of(), 0);
    }

    @Test
    void lesDependantsEtLAgregateurRejoignentLePerimetreAvecLeurChaine() {
        declareRoot();
        Module common = module("libs/common-contract", "common-contract");
        Module quarkus = module("libs/quarkus-contract", "quarkus-contract");
        Module facturation = module("modules/facturation", "facturation");
        Module relance = module("modules/relance", "relance");
        Module aggregates = module("", "aggregates");
        Module annuaire = module("modules/annuaire", "annuaire");
        Map<ModuleId, ModuleDescriptor> poms = new LinkedHashMap<>();
        poms.put(aggregates.id(), new ModuleDescriptor("aggregates", "fr.demo:aggregates", null, Set.of(), Set.of(),
                List.of("libs/common-contract", "modules/relance"), 0));
        poms.put(common.id(), pom("common-contract"));
        poms.put(quarkus.id(), pom("quarkus-contract", "fr.demo:common-contract"));
        poms.put(facturation.id(), pom("facturation", "fr.demo:quarkus-contract"));
        poms.put(relance.id(), pom("relance", "fr.demo:facturation"));
        poms.put(annuaire.id(), pom("annuaire"));
        List<SourceTree.ModuleHit> hits = List.of(
                new SourceTree.ModuleHit(common, Map.of("ContractStatus", 3), List.of()),
                new SourceTree.ModuleHit(facturation, Map.of("ContractStatus", 1), List.of()));
        scan = new SourceTree.Scan(hits, List.of(aggregates, common, quarkus, facturation, relance, annuaire), poms,
                List.of(new PerimeterAnalysis.RootStatus(ROOT, "rv", PerimeterAnalysis.RootState.SCANNED)), 40, 0,
                List.of(), null);

        PerimeterAnalysis analysis = analyze.analyze(PROJECT, List.of("ContractStatus"), null, 1, () -> false);

        assertThat(analysis.modules()).extracting(m -> m.module().name()).containsExactly("common-contract",
                "facturation", "aggregates", "quarkus-contract", "relance");
        PerimeterModule f = analysis.modules().get(1);
        // trouvée par un terme et par dépendance : une seule ligne, deux raisons (FR-020)
        assertThat(f.reasons()).containsExactly(new Reason.Term("ContractStatus", 1), new Reason.Dependency(List.of(
                ModuleRef.of(common), ModuleRef.of(quarkus), ModuleRef.of(facturation))));
        assertThat(f.qualification()).isEqualTo(Qualification.UNQUALIFIED);
        PerimeterModule r = analysis.modules().get(4);
        assertThat(r.reasons()).containsExactly(new Reason.Dependency(List.of(ModuleRef.of(facturation),
                ModuleRef.of(relance))));
        assertThat(r.qualification()).isEqualTo(Qualification.TO_CHECK);
        assertThat(r.occurrences()).isZero();
        assertThat(analysis.modules().get(2).reasons()).containsExactly(new Reason.Aggregator(ModuleRef.of(common)));
    }
}
