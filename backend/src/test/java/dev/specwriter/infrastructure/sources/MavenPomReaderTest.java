package dev.specwriter.infrastructure.sources;

import static org.assertj.core.api.Assertions.assertThat;

import dev.specwriter.domain.sources.ModuleDescriptor;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenPomReaderTest {

    @TempDir
    Path dir;

    static ModuleDescriptor read(String xml) {
        return MavenPomReader.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void leGroupIdAbsentEstHeriteDuParent() {
        ModuleDescriptor pom = read("""
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <parent><groupId>fr.demo</groupId><artifactId>super</artifactId><version>1</version></parent>
                  <artifactId>facturation</artifactId>
                </project>""");
        assertThat(pom.coordinates()).isEqualTo("fr.demo:facturation");
        assertThat(pom.parent()).isEqualTo("fr.demo:super");
    }

    @Test
    void lesDependancesLesImportsDeBomEtLesModulesAgreges() {
        ModuleDescriptor pom = read("""
                <project>
                  <groupId>fr.demo</groupId><artifactId>facturation</artifactId>
                  <modules><module>a</module><module>./b/</module></modules>
                  <dependencyManagement><dependencies>
                    <dependency><groupId>fr.demo</groupId><artifactId>bom</artifactId><type>pom</type><scope>import</scope></dependency>
                    <dependency><groupId>fr.demo</groupId><artifactId>gere</artifactId><version>1</version></dependency>
                  </dependencies></dependencyManagement>
                  <dependencies>
                    <dependency><groupId>fr.demo</groupId><artifactId>quarkus-contract</artifactId></dependency>
                    <dependency><groupId>${groupe}</groupId><artifactId>x</artifactId></dependency>
                  </dependencies>
                  <profiles><profile><dependencies>
                    <dependency><groupId>fr.demo</groupId><artifactId>profil</artifactId></dependency>
                  </dependencies></profile></profiles>
                  <build><plugins><plugin><dependencies>
                    <dependency><groupId>fr.demo</groupId><artifactId>plugin-dep</artifactId></dependency>
                  </dependencies></plugin></plugins></build>
                </project>""");
        assertThat(pom.dependencies()).containsExactlyInAnyOrder("fr.demo:quarkus-contract", "fr.demo:profil");
        assertThat(pom.bomImports()).containsExactly("fr.demo:bom");
        assertThat(pom.aggregated()).containsExactly("a", "b");
        assertThat(pom.unresolved()).isEqualTo(1);
    }

    @Test
    void unDoctypeEtUneEntiteExterneSontRefusesSansLireLeFichierVise() throws Exception {
        Path secret = Files.writeString(dir.resolve("secret.txt"), "MOT-DE-PASSE");
        ModuleDescriptor pom = read("<?xml version=\"1.0\"?><!DOCTYPE p [<!ENTITY x SYSTEM \"" + secret.toUri()
                + "\">]><project><groupId>g</groupId><artifactId>&x;</artifactId></project>");
        assertThat(pom).isEqualTo(ModuleDescriptor.unreadable());
    }

    @Test
    void unPomInvalideNeLevePasDException() {
        assertThat(read("<project><artifactId>")).isEqualTo(ModuleDescriptor.unreadable());
        assertThat(read("")).isEqualTo(ModuleDescriptor.unreadable());
    }

    @Test
    void desCoordonneesNonResoluesNeSontPasRetenues() {
        ModuleDescriptor pom = read("<project><groupId>${g}</groupId><artifactId>a</artifactId></project>");
        assertThat(pom.coordinates()).isNull();
        assertThat(pom.unresolved()).isEqualTo(1);
        assertThat(pom.artifactId()).isEqualTo("a");
    }
}
