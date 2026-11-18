package fr.kawamind.infrastructure.confluence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.kawamind.domain.port.ConfluenceClient;
import fr.kawamind.domain.port.ConfluenceClient.ConfluenceException;
import fr.kawamind.domain.port.ConfluenceClient.Failure;
import org.junit.jupiter.api.Test;

/** T020 (016) : trois formes d'adresse, chemin de contexte, autre hôte refusé. */
class ConfluencePageAddressTest {

    static final String SERVER = "https://wiki.example.com/confluence";

    @Test
    void troisFormes() {
        assertThat(ConfluencePageAddress.parse(SERVER, SERVER + "/pages/viewpage.action?pageId=123").pageId())
                .isEqualTo("123");
        assertThat(ConfluencePageAddress.parse(SERVER, SERVER + "/pages/viewpage.action?spaceKey=K&pageId=7").pageId())
                .isEqualTo("7");
        assertThat(ConfluencePageAddress.parse(SERVER, SERVER + "/spaces/KEY/pages/123/Titre").pageId())
                .isEqualTo("123");
        ConfluencePageAddress display = ConfluencePageAddress.parse(SERVER, SERVER + "/display/KEY/Titre+de+la+page");
        assertThat(display.byTitle()).isTrue();
        assertThat(display.spaceKey()).isEqualTo("KEY");
        assertThat(display.title()).isEqualTo("Titre de la page");
    }

    @Test
    void serveurSansCheminDeContexte() {
        assertThat(ConfluencePageAddress.parse("https://wiki.example.com", "https://WIKI.example.com:443/pages/viewpage.action?pageId=9")
                .pageId()).isEqualTo("9");
    }

    @Test
    void autreHoteOuAutreChemin() {
        assertThatThrownBy(() -> ConfluencePageAddress.parse(SERVER, "https://autre.example.com/confluence/pages/viewpage.action?pageId=1"))
                .satisfies(e -> assertThat(((ConfluenceException) e).failure()).isEqualTo(Failure.OTHER_HOST));
        assertThatThrownBy(() -> ConfluencePageAddress.parse(SERVER, "https://wiki.example.com/autre/pages/viewpage.action?pageId=1"))
                .satisfies(e -> assertThat(((ConfluenceException) e).failure()).isEqualTo(Failure.OTHER_HOST));
        assertThatThrownBy(() -> ConfluencePageAddress.parse(SERVER, "http://wiki.example.com/confluence/pages/viewpage.action?pageId=1"))
                .satisfies(e -> assertThat(((ConfluenceException) e).failure()).isEqualTo(Failure.OTHER_HOST));
    }

    @Test
    void pasUnePage() {
        assertThatThrownBy(() -> ConfluencePageAddress.parse(SERVER, SERVER + "/dashboard.action"))
                .satisfies(e -> assertThat(((ConfluenceException) e).failure()).isEqualTo(Failure.NOT_A_PAGE));
        assertThatThrownBy(() -> ConfluencePageAddress.parse(SERVER, "pas une adresse"))
                .satisfies(e -> assertThat(((ConfluenceException) e).failure()).isEqualTo(Failure.NOT_A_PAGE));
    }

    @Test
    void adresseCanoniqueDUnePage() {
        assertThat(ConfluenceClient.pageRef(SERVER, "42")).isEqualTo(SERVER + "/pages/viewpage.action?pageId=42");
        assertThat(ConfluenceClient.pageId(SERVER + "/pages/viewpage.action?pageId=42#3")).contains("42");
        assertThat(ConfluenceClient.pageId("docs:x/Demande.docx#2")).isEmpty();
    }
}
