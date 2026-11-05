package dev.specwriter.infrastructure.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.structurizr.dsl.Features;
import com.structurizr.dsl.StructurizrDslParser;
import com.structurizr.model.Container;
import java.io.File;
import org.junit.jupiter.api.Test;

/** Vérifie que le jeu fictif Structurizr se charge en mode restreint (T090). */
class StructurizrFixtureSmokeIT {

  private static final File C4 = new File("src/test/resources/knowledge/c4");

  private static StructurizrDslParser parser() {
    StructurizrDslParser parser = new StructurizrDslParser();
    parser.setRestricted(true);
    parser.getFeatures().enable(Features.FILE_SYSTEM);
    parser.getFeatures().enable(Features.INCLUDE);
    return parser;
  }

  @Test
  void leWorkspaceEtenduAgregeLeCommunEtLePropre() throws Exception {
    StructurizrDslParser parser = parser();
    parser.parse(new File(C4, "facturation/workspace.dsl"));
    var model = parser.getWorkspace().getModel();
    assertThat(model.getSoftwareSystems()).extracting("name")
        .contains("Facturation", "Comptabilite", "Suez");
    assertThat(model.getElements()).filteredOn(Container.class::isInstance).extracting("name")
        .contains("avoirs-emis", "Ecritures", "Service Avoirs", "Portail Client");
    assertThat(model.getDeploymentNodes()).extracting("environment").containsOnly("Production");
    assertThat(model.getDeploymentNodes()).extracting("name").containsExactlyInAnyOrder("ITaaS", "Azure");
    assertThat(model.getElements()).filteredOn(com.structurizr.model.ContainerInstance.class::isInstance)
        .hasSize(4);
  }

  @Test
  void unIncludeAbsentEchoue() {
    assertThatThrownBy(() -> parser().parse(new File(C4, "broken/workspace.dsl")))
        .hasMessageContaining("absent.dsl");
  }

  @Test
  void unScriptEstRefuseEnModeRestreint() {
    assertThatThrownBy(() -> parser().parse(new File(C4, "scripted/workspace.dsl")))
        .hasMessageContaining("script");
  }
}
