package dev.specwriter.infrastructure.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import dev.specwriter.domain.knowledge.ArchDeployment;
import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchRelation;
import dev.specwriter.domain.knowledge.ArchScope;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.port.ArchitectureReader;
import dev.specwriter.domain.port.ArchitectureReader.Result;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Lecture du workspace Structurizr fictif (T099, research.md R21). */
class StructurizrReaderTest {

    static Path c4;
    static Result facturation;
    final StructurizrReader reader = new StructurizrReader();

    @BeforeAll
    static void lire() throws URISyntaxException {
        c4 = Path.of(StructurizrReaderTest.class.getResource("/knowledge/c4").toURI());
        facturation = new StructurizrReader().read(c4.resolve("facturation/workspace.dsl").toString(), "facturation");
    }

    static ArchElement element(String name) {
        return facturation.model().element(name).orElseThrow(() -> new AssertionError("absent : " + name));
    }

    @Test
    void extendsEtIncludeResolus() {
        assertThat(facturation.failed()).isFalse();
        assertThat(facturation.files()).extracting(f -> Path.of(f).getFileName().toString())
                .containsExactlyInAnyOrder("workspace.dsl", "model.dsl", "prod.dsl");
        assertThat(facturation.hash()).matches("[0-9a-f]{64}");
    }

    @Test
    void nomsCanoniques() {
        assertThat(facturation.model().elements()).extracting(ArchElement::name).contains("Client", "Facturation",
                "Facturation/API Facturation", "Facturation/Service Avoirs", "Facturation/Portail Client",
                "Comptabilite", "Comptabilite/Ecritures", "Comptabilite/Grand Livre", "Suez", "Suez/avoirs-emis");
        assertThat(element("Client").type()).isEqualTo(ArchElement.Type.PERSON);
        assertThat(element("Facturation/API Facturation").type()).isEqualTo(ArchElement.Type.CONTAINER);
        assertThat(element("Facturation/API Facturation").technology()).isEqualTo("Quarkus");
        assertThat(element("Facturation/Service Avoirs").description()).isEqualTo("Calcule et émet les avoirs");
    }

    @Test
    void transportReconnuParLArchetype() {
        assertThat(element("Suez/avoirs-emis").transport()).isTrue();
        assertThat(element("Facturation/Service Avoirs").transport()).isFalse();
    }

    @Test
    void ownershipPrioritairePuisModeleEtendu() {
        // ownership « comptabilite » : commun, bien qu'ajouté par le workspace
        assertThat(element("Comptabilite/Grand Livre").scope()).isEqualTo(ArchScope.COMMON);
        assertThat(element("Facturation/Service Avoirs").scope()).isEqualTo(ArchScope.PROJECT);
        // présents dans le modèle étendu : communs
        assertThat(element("Client").scope()).isEqualTo(ArchScope.COMMON);
        assertThat(element("Comptabilite").scope()).isEqualTo(ArchScope.COMMON);
        assertThat(element("Suez").scope()).isEqualTo(ArchScope.COMMON);
        // ajoutés par le workspace, y compris dans un système commun : propres
        assertThat(element("Facturation").scope()).isEqualTo(ArchScope.PROJECT);
        assertThat(element("Comptabilite/Ecritures").scope()).isEqualTo(ArchScope.PROJECT);
        assertThat(element("Suez/avoirs-emis").scope()).isEqualTo(ArchScope.PROJECT);
    }

    @Test
    void relationsExplicitesSeulement() {
        assertThat(facturation.model().relations()).extracting(ArchRelation::source, ArchRelation::destination)
                .containsExactlyInAnyOrder(
                        tuple("Client", "Facturation/Portail Client"),
                        tuple("Facturation/Portail Client", "Facturation/API Facturation"),
                        tuple("Facturation/API Facturation", "Facturation/Service Avoirs"),
                        tuple("Facturation/Service Avoirs", "Suez/avoirs-emis"),
                        tuple("Comptabilite/Ecritures", "Suez/avoirs-emis"),
                        tuple("Comptabilite/Ecritures", "Comptabilite/Grand Livre"));
        assertThat(facturation.model().relations()).filteredOn(r -> r.source().equals("Facturation/Service Avoirs"))
                .singleElement().extracting(ArchRelation::technology).isEqualTo("Kafka");
    }

    @Test
    void deploiementsEtNoeudsPartages() {
        assertThat(facturation.model().deployments())
                .extracting(ArchDeployment::element, ArchDeployment::environment, ArchDeployment::rootNode,
                        ArchDeployment::nodePath, ArchDeployment::sharedInfrastructure)
                .containsExactlyInAnyOrder(
                        tuple("Facturation/API Facturation", "Production", "Azure", "Azure/AKS", false),
                        tuple("Facturation/Service Avoirs", "Production", "Azure", "Azure/AKS", false),
                        tuple("Comptabilite/Ecritures", "Production", "ITaaS", "ITaaS/OpenShift", true),
                        tuple("Facturation/API Facturation", "Production", "ITaaS", "ITaaS/OpenShift", true));
    }

    @Test
    void lesFaitsDuJeuFictif() {
        ArchitectureModel model = facturation.model();
        assertThat(model.exchange("Facturation/Service Avoirs", "Comptabilite/Ecritures").kind())
                .isEqualTo(ArchitectureModel.Exchange.Kind.INDIRECT);
        assertThat(model.exchange("Facturation/API Facturation", "Comptabilite/Ecritures").kind())
                .isEqualTo(ArchitectureModel.Exchange.Kind.MISSING);
        assertThat(model.deploymentFacts(java.util.List.of("Facturation/Portail Client"), java.util.List.of()))
                .extracting(ArchitectureModel.DeploymentFact::kind)
                .containsExactly(ArchitectureModel.DeploymentFact.Kind.NOT_DEPLOYED);
    }

    @Test
    void sansOwnershipDeclareLeModeleEtenduDecide() {
        Result r = reader.read(c4.resolve("facturation/workspace.dsl").toString(), null);
        assertThat(r.model().element("Comptabilite/Grand Livre").orElseThrow().scope()).isEqualTo(ArchScope.PROJECT);
        assertThat(r.model().element("Client").orElseThrow().scope()).isEqualTo(ArchScope.COMMON);
    }

    @Test
    void includeIntrouvableAvecFichierEtLigne() {
        Result r = reader.read(c4.resolve("broken/workspace.dsl").toString(), null);
        assertThat(r.failed()).isTrue();
        assertThat(r.problem().file()).endsWith("workspace.dsl");
        assertThat(r.problem().line()).isEqualTo(4);
        assertThat(r.problem().message()).contains("absent.dsl");
        assertThat(r.model()).isEqualTo(ArchitectureModel.EMPTY);
    }

    /** Les scripts sont ignorés, jamais exécutés : un avertissement par script, avec fichier et ligne. */
    @Test
    void scriptsIgnoresAvecUnAvertissement() {
        Result r = reader.read(c4.resolve("scripted/workspace.dsl").toString(), null);
        assertThat(r.failed()).isFalse();
        assertThat(r.model().element("Scripté")).isPresent();
        assertThat(r.warnings()).extracting(ArchitectureReader.Problem::line).containsExactly(4, 10);
        assertThat(r.warnings()).allSatisfy(w -> {
            assertThat(w.file()).endsWith("scripted/workspace.dsl");
            assertThat(w.message()).contains("!script ignoré");
        });
        // le script n'a pas tourné : le workspace garde son nom, l'élément n'a pas reçu de tag
        assertThat(r.warnings()).noneMatch(w -> w.message().contains("piégé"));
        assertThat(r.files()).noneMatch(f -> f.endsWith(".groovy"));
    }

    @Test
    void sansScriptAucunAvertissement() {
        assertThat(facturation.warnings()).isEmpty();
    }

    @Test
    void workspaceAbsentOuIllisible(@TempDir Path tmp) throws IOException {
        assertThat(reader.read(tmp.resolve("absent.dsl").toString(), null).problem().message())
                .contains("introuvable");
        Path folder = Files.createDirectories(tmp.resolve("dossier"));
        assertThat(reader.read(folder.toString(), null).failed()).isTrue();
    }

    @Test
    void workspaceSansExtends(@TempDir Path tmp) throws IOException {
        Path copy = tmp.resolve("seul");
        try (Stream<Path> files = Files.walk(c4)) {
            for (Path f : files.sorted(Comparator.naturalOrder()).toList()) {
                Path target = copy.resolve(c4.relativize(f).toString());
                if (Files.isDirectory(f)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(f, target);
                }
            }
        }
        Files.writeString(copy.resolve("simple.dsl"), """
                workspace {
                    model {
                        a = softwareSystem "A" {
                            api = container "Api" {
                                properties {
                                    "spec-writer.module" " modules/api "
                                }
                            }
                            batch = container "Batch"
                        }
                    }
                }
                """);
        Result r = reader.read(copy.resolve("simple.dsl").toString(), null);
        assertThat(r.failed()).isFalse();
        assertThat(r.model().elements()).extracting(ArchElement::name, ArchElement::scope)
                .containsExactlyInAnyOrder(tuple("A", ArchScope.PROJECT), tuple("A/Api", ArchScope.PROJECT),
                        tuple("A/Batch", ArchScope.PROJECT));
        // 013 : la propriété spec-writer.module désigne le module de code ; sans elle, rien
        assertThat(r.model().elements()).extracting(ArchElement::name, ArchElement::moduleHint)
                .containsExactlyInAnyOrder(tuple("A", null), tuple("A/Api", "modules/api"), tuple("A/Batch", null));
        assertThat(r.model().deployments()).isEmpty();
    }

    /** {@code !docs} et {@code !adrs} lisent des fichiers locaux : le modèle est lu (cas des workspaces réels). */
    @Test
    void docsEtAdrsAcceptes(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("docs"));
        Files.writeString(tmp.resolve("docs/01-intro.md"), "## Introduction\n\nTexte.\n");
        Files.createDirectories(tmp.resolve("adrs"));
        Files.writeString(tmp.resolve("adrs/0001-choix.md"), """
                # 1. Choix

                Date: 2026-01-01

                ## Status

                Accepted

                ## Context

                Contexte.
                """);
        Files.writeString(tmp.resolve("workspace.dsl"), """
                workspace {
                    !docs docs
                    !adrs adrs
                    model {
                        a = softwareSystem "A"
                    }
                }
                """);
        Result r = reader.read(tmp.resolve("workspace.dsl").toString(), null);
        assertThat(r.problem()).isNull();
        assertThat(r.model().elements()).extracting(ArchElement::name).containsExactly("A");
    }
}
