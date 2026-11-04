package dev.specwriter.domain.knowledge;

import static dev.specwriter.domain.knowledge.ArchElement.Type.CONTAINER;
import static dev.specwriter.domain.knowledge.ArchElement.Type.PERSON;
import static dev.specwriter.domain.knowledge.ArchElement.Type.SOFTWARE_SYSTEM;
import static dev.specwriter.domain.knowledge.ArchScope.COMMON;
import static dev.specwriter.domain.knowledge.ArchScope.PROJECT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.knowledge.ArchitectureModel.DeploymentFact;
import dev.specwriter.domain.knowledge.ArchitectureModel.Exchange;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Modèle du jeu fictif (BTR/knowledge/c4/facturation). */
class ArchitectureModelTest {

    private static final String API = "Facturation/API Facturation";
    private static final String AVOIRS = "Facturation/Service Avoirs";
    private static final String PORTAIL = "Facturation/Portail Client";
    private static final String TOPIC = "Suez/avoirs-emis";
    private static final String ECRITURES = "Comptabilite/Ecritures";
    private static final String GRAND_LIVRE = "Comptabilite/Grand Livre";

    private static ArchElement element(String name, ArchElement.Type type, ArchScope scope, boolean transport) {
        return new ArchElement(name, type, scope, transport, null, null);
    }

    private static final ArchitectureModel MODEL = new ArchitectureModel(
            List.of(element("Client", PERSON, COMMON, false),
                    element("Facturation", SOFTWARE_SYSTEM, PROJECT, false),
                    element(API, CONTAINER, PROJECT, false),
                    element(AVOIRS, CONTAINER, PROJECT, false),
                    element(PORTAIL, CONTAINER, PROJECT, false),
                    element("Comptabilite", SOFTWARE_SYSTEM, COMMON, false),
                    element(ECRITURES, CONTAINER, PROJECT, false),
                    element(GRAND_LIVRE, CONTAINER, COMMON, false),
                    element("Suez", SOFTWARE_SYSTEM, COMMON, false),
                    element(TOPIC, CONTAINER, PROJECT, true)),
            List.of(new ArchRelation("Client", PORTAIL, "Consulte ses factures", "HTTPS"),
                    new ArchRelation(PORTAIL, API, "Lit les factures", "HTTPS"),
                    new ArchRelation(API, AVOIRS, "Demande un avoir", "HTTPS"),
                    new ArchRelation(AVOIRS, TOPIC, "Publie les avoirs émis", "Kafka"),
                    new ArchRelation(ECRITURES, TOPIC, "Consomme les avoirs émis", "Kafka"),
                    new ArchRelation(ECRITURES, GRAND_LIVRE, "Reporte les écritures", "JDBC")),
            List.of(new ArchDeployment(API, "Production", "Azure", "Azure/AKS", 1, false),
                    new ArchDeployment(AVOIRS, "Production", "Azure", "Azure/AKS", 1, false),
                    new ArchDeployment(ECRITURES, "Production", "ITaaS", "ITaaS/OpenShift", 1, true),
                    new ArchDeployment(API, "Production", "ITaaS", "ITaaS/OpenShift", 1, true),
                    new ArchDeployment(PORTAIL, "Recette", "Azure", "Azure/AKS", 1, false)));

    @Test
    void fluxDirectDansUnSensOuDansLAutre() {
        assertThat(MODEL.exchange(PORTAIL, API)).isEqualTo(Exchange.direct(PORTAIL, API));
        assertThat(MODEL.exchange(API, PORTAIL)).isEqualTo(Exchange.direct(API, PORTAIL));
    }

    @Test
    void fluxDirectEntreDescendants() {
        assertThat(MODEL.exchange("Facturation", "Client").kind()).isEqualTo(Exchange.Kind.DIRECT);
        assertThat(MODEL.exchange(GRAND_LIVRE, "Comptabilite/Ecritures").kind()).isEqualTo(Exchange.Kind.DIRECT);
    }

    @Test
    void fluxIndirectParUnSeulIntermediaireDeTransport() {
        Exchange exchange = MODEL.exchange(AVOIRS, ECRITURES);
        assertThat(exchange.kind()).isEqualTo(Exchange.Kind.INDIRECT);
        assertThat(exchange.via()).isEqualTo(TOPIC);
        assertThat(MODEL.exchange("Facturation", "Comptabilite").kind()).isEqualTo(Exchange.Kind.INDIRECT);
    }

    @Test
    void pasDeFluxParDeuxIntermediaires() {
        // API -> Avoirs -> topic -> Ecritures : Avoirs n'est pas un transport
        assertThat(MODEL.exchange(API, ECRITURES).kind()).isEqualTo(Exchange.Kind.MISSING);
        assertThat(MODEL.exchange(PORTAIL, AVOIRS).kind()).isEqualTo(Exchange.Kind.MISSING);
        assertThat(MODEL.exchange("Client", GRAND_LIVRE).kind()).isEqualTo(Exchange.Kind.MISSING);
    }

    @Test
    void unModuleInconnuNEchangeAvecRien() {
        assertThat(MODEL.exchange(API, "Inconnu").kind()).isEqualTo(Exchange.Kind.MISSING);
    }

    @Test
    void intermediaireDeTransportParTagArchetypeOuTechnologie() {
        assertThat(ArchitectureModel.isTransport(Set.of("Element", "Container", "topic"), null)).isTrue();
        assertThat(ArchitectureModel.isTransport(Set.of("Queue"), null)).isTrue();
        assertThat(ArchitectureModel.isTransport(Set.of("bus"), null)).isTrue();
        assertThat(ArchitectureModel.isTransport(Set.of(), "Apache Kafka")).isTrue();
        assertThat(ArchitectureModel.isTransport(Set.of(), "RabbitMQ (AMQP)")).isTrue();
        assertThat(ArchitectureModel.isTransport(Set.of(), "IBM MQ")).isTrue();
        assertThat(ArchitectureModel.isTransport(Set.of(), "JMS")).isTrue();
        assertThat(ArchitectureModel.isTransport(Set.of("Container"), "Quarkus")).isFalse();
        assertThat(ArchitectureModel.isTransport(Set.of(), "Mquery")).isFalse();
        assertThat(ArchitectureModel.isTransport(null, null)).isFalse();
    }

    @Test
    void moduleSansInstanceDeProduction() {
        assertThat(MODEL.deploymentFacts(List.of(PORTAIL), List.of()))
                .contains(new DeploymentFact(DeploymentFact.Kind.NOT_DEPLOYED, PORTAIL, null));
        // un système est déployé si l'un de ses conteneurs l'est
        assertThat(MODEL.deploymentFacts(List.of("Facturation"), List.of()))
                .noneMatch(f -> f.kind() == DeploymentFact.Kind.NOT_DEPLOYED);
        // une personne ne se déploie pas
        assertThat(MODEL.deploymentFacts(List.of("Client"), List.of())).isEmpty();
    }

    @Test
    void moduleEnPlusieursInstances() {
        assertThat(MODEL.deploymentFacts(List.of(API), List.of()))
                .contains(new DeploymentFact(DeploymentFact.Kind.MULTIPLE_INSTANCES, API, null));
        assertThat(MODEL.deploymentFacts(List.of(AVOIRS), List.of()))
                .noneMatch(f -> f.kind() == DeploymentFact.Kind.MULTIPLE_INSTANCES);
        ArchitectureModel scaled = new ArchitectureModel(MODEL.elements(), MODEL.relations(),
                List.of(new ArchDeployment(AVOIRS, "prod-eu", "Azure", "Azure/AKS", 3, false)));
        assertThat(scaled.deploymentFacts(List.of(AVOIRS), List.of()))
                .containsExactly(new DeploymentFact(DeploymentFact.Kind.MULTIPLE_INSTANCES, AVOIRS, null));
    }

    @Test
    void echangeEntreNoeudsRacinesDifferents() {
        assertThat(MODEL.deploymentFacts(List.of(AVOIRS, ECRITURES), List.of(List.of(AVOIRS, ECRITURES))))
                .contains(new DeploymentFact(DeploymentFact.Kind.CROSS_INFRASTRUCTURE, AVOIRS, ECRITURES));
        // l'API a une instance sur ITaaS, comme les écritures
        assertThat(MODEL.deploymentFacts(List.of(API, ECRITURES), List.of(List.of(API, ECRITURES))))
                .noneMatch(f -> f.kind() == DeploymentFact.Kind.CROSS_INFRASTRUCTURE);
    }

    @Test
    void moduleProprDansUnSystemeCommunOuSurUnNoeudPartage() {
        assertThat(MODEL.deploymentFacts(List.of(ECRITURES), List.of()))
                .contains(new DeploymentFact(DeploymentFact.Kind.SHARED_INFRASTRUCTURE, ECRITURES, null));
        assertThat(MODEL.deploymentFacts(List.of(API), List.of()))
                .contains(new DeploymentFact(DeploymentFact.Kind.SHARED_INFRASTRUCTURE, API, null));
        assertThat(MODEL.deploymentFacts(List.of(AVOIRS), List.of())).isEmpty();
        // un module commun n'appelle pas d'effort propre
        assertThat(MODEL.deploymentFacts(List.of(GRAND_LIVRE), List.of()))
                .noneMatch(f -> f.kind() == DeploymentFact.Kind.SHARED_INFRASTRUCTURE);
    }

    @Test
    void seulsLesEnvironnementsDeProductionComptent() {
        assertThat(ArchitectureModel.isProduction("Production")).isTrue();
        assertThat(ArchitectureModel.isProduction("PROD-EU")).isTrue();
        assertThat(ArchitectureModel.isProduction("Recette")).isFalse();
        assertThat(ArchitectureModel.isProduction(null)).isFalse();
    }
}
