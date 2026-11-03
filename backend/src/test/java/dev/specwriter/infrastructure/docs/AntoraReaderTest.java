package dev.specwriter.infrastructure.docs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.specwriter.domain.port.DocumentationReader;
import dev.specwriter.domain.port.DocumentationReader.Page;
import dev.specwriter.domain.port.DocumentationReader.Result;
import dev.specwriter.domain.port.DocumentationReader.Unread;
import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import dev.specwriter.domain.project.DocSource;
import dev.specwriter.infrastructure.platform.ProxySettings;
import dev.specwriter.support.FixedClock;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Lecture d'un site Antora par son sitemap, son dossier ou ses liens (research.md R3 de 002, T020). */
class AntoraReaderTest {

    /** Adresse figée dans le sitemap du site de test (celle de WireMock en E2E). */
    static final String FIXTURE_BASE = "http://127.0.0.1:47698";

    static WireMockServer site;
    static Path folder;

    final FixedClock clock = new FixedClock();
    AntoraReader reader;

    @BeforeAll
    static void start() throws URISyntaxException {
        site = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        site.start();
        folder = Path.of(AntoraReaderTest.class.getResource("/antora-site").toURI());
    }

    @AfterAll
    static void stop() {
        site.stop();
    }

    @BeforeEach
    void setUp() {
        site.resetAll();
        serveFixture();
        reader = new AntoraReader(ProxySettings.from(Map.of(), new Properties()), clock, AntoraReader.Limits.DEFAULT);
    }

    String base() {
        return "http://127.0.0.1:" + site.port();
    }

    /** Le site de test sous /antora/, ses adresses absolues réécrites vers le port de WireMock. */
    void serveFixture() {
        try (Stream<Path> files = Files.walk(folder)) {
            files.filter(Files::isRegularFile).forEach(f -> {
                String rel = folder.relativize(f).toString().replace('\\', '/');
                String body = read(f).replace(FIXTURE_BASE, base());
                String type = rel.endsWith(".xml") ? "application/xml" : "text/html; charset=utf-8";
                site.stubFor(get(urlPathEqualTo("/antora/" + rel))
                        .willReturn(aResponse().withHeader("Content-Type", type).withBody(body)));
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        site.stubFor(get(urlPathEqualTo("/antora/billing/tarifs-2025.html")).atPriority(1)
                .willReturn(aResponse().withStatus(500)));
    }

    static String read(Path f) {
        try {
            return Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    DocSource url(String location) {
        return DocSource.create(UUID.randomUUID(), UUID.randomUUID(), DocSource.Kind.URL, location);
    }

    @Test
    void indexDeSitemapsPuisPagesArticleDocSeulement() {
        DocSource source = url(base() + "/antora/");
        assertThat(reader.count(source)).isEqualTo(32);

        Result r = reader.read(source);
        assertThat(r.pages()).hasSize(30).extracting(Page::ref).contains(base() + "/antora/billing/regle-tva.html");
        Page tva = r.pages().stream().filter(p -> p.ref().endsWith("regle-tva.html")).findFirst().orElseThrow();
        assertThat(tva.title()).isEqualTo("Taux de TVA");
        // titres, paragraphes et tableaux conservés en texte ; ni navigation, ni en-tête, ni pied de page
        assertThat(tva.text()).contains("# Taux de TVA", "Chaque ligne de facturation porte son taux de TVA.",
                "| Prestation | Taux |", "| Livres | 5,5 % |");
        assertThat(tva.text()).doesNotContain("Rechercher", "Fixture de test", "Glossaire");
        Page acteur = r.pages().stream().filter(p -> p.ref().endsWith("acteur-comptable.html")).findFirst().orElseThrow();
        assertThat(acteur.text()).contains("## Droits", "- lettrer une facture");

        assertThat(r.unread()).containsExactlyInAnyOrder(
                new Unread(base() + "/antora/billing/tarifs-2025.html", UnreadReason.HTTP_ERROR, 500),
                new Unread(base() + "/antora/billing/recherche.html", UnreadReason.NOT_ANTORA));
        assertThat(r.limitReached()).isFalse();
    }

    @Test
    void chaquePageSignaleeEtPagesNonLuesJournaliseesSansContenu() {
        List<String> seen = Collections.synchronizedList(new ArrayList<>());
        List<String> logged = Collections.synchronizedList(new ArrayList<>());
        java.util.logging.Logger logger = java.util.logging.Logger.getLogger(AntoraReader.class.getName());
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getLevel() + " " + new java.util.logging.SimpleFormatter().formatMessage(record));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(capture);
        try {
            reader.read(url(base() + "/antora/"), (location, done, total) -> seen.add(location + " " + total));
        } finally {
            logger.removeHandler(capture);
        }
        assertThat(seen).hasSize(32).allSatisfy(s -> assertThat(s).endsWith(" 32"))
                .contains(base() + "/antora/billing/regle-tva.html 32");
        assertThat(logged).singleElement().satisfies(l -> assertThat(l)
                .startsWith("WARN").contains("tarifs-2025.html", "HTTP_ERROR", "HTTP 500")
                .doesNotContain("TVA"));
    }

    @Test
    void lectureInterrompueAvantLaPageSuivante() {
        int[] count = {0};
        Result r = reader.read(url(base() + "/antora/"), new DocumentationReader.Listener() {
            @Override
            public void reading(String location, int done, int total) {
                count[0]++;
            }

            @Override
            public boolean cancelled() {
                return count[0] >= 5;
            }
        });
        assertThat(count[0]).isBetween(5, 5 + 4);
        assertThat(r.pages().size() + r.unread().size()).isLessThan(32);
        assertThat(r.limitReached()).isFalse();
    }

    @Test
    void dossierLocalDuSiteGenere() {
        DocSource source = DocSource.create(UUID.randomUUID(), UUID.randomUUID(), DocSource.Kind.FOLDER, folder.toString());
        assertThat(reader.count(source)).isEqualTo(31);
        Result r = reader.read(source);
        assertThat(r.pages()).hasSize(30);
        assertThat(r.pages()).extracting(Page::ref).contains("billing/glossaire-avoir.html");
        assertThat(r.unread()).containsExactly(new Unread("billing/recherche.html", UnreadReason.NOT_ANTORA));
    }

    @Test
    void sansSitemapLesLiensDuMemeHoteSousLeMemeChemin() {
        site.stubFor(get(urlPathEqualTo("/antora/sitemap.xml")).atPriority(1).willReturn(notFound()));
        site.stubFor(get(urlEqualTo("/antora/")).atPriority(1)
                .willReturn(aResponse().withStatus(302).withHeader("Location", "/antora/billing/index.html")));
        Result r = reader.read(url(base() + "/antora/"));
        // tarifs-2025 n'est cité par aucun lien : seules les pages de la navigation sont lues
        assertThat(r.pages()).hasSize(30);
        assertThat(r.unread()).extracting(Unread::reason).containsOnly(UnreadReason.NOT_ANTORA);
        // le lien vers service-public.fr n'est jamais suivi
        assertThat(r.pages()).extracting(Page::ref).noneMatch(ref -> ref.contains("service-public"));
    }

    @Test
    void redirectionVersUnAutreHoteNonSuivie() {
        site.stubFor(get(urlPathEqualTo("/antora/billing/regle-tva.html")).atPriority(1)
                .willReturn(aResponse().withStatus(301).withHeader("Location", "https://autre-hote.example/tva.html")));
        Result r = reader.read(url(base() + "/antora/"));
        assertThat(r.unread()).contains(new Unread(base() + "/antora/billing/regle-tva.html", UnreadReason.REDIRECT_OTHER_HOST, 301));
        assertThat(r.pages()).hasSize(29);
    }

    @Test
    void pageDePlusDe2MoNonLue() {
        String big = "<html><body><article class=\"doc\"><p>" + "x".repeat(2 * 1024 * 1024) + "</p></article></body></html>";
        site.stubFor(get(urlPathEqualTo("/antora/billing/regle-pdf.html")).atPriority(1)
                .willReturn(aResponse().withHeader("Content-Type", "text/html").withBody(big)));
        Result r = reader.read(url(base() + "/antora/"));
        assertThat(r.unread()).contains(new Unread(base() + "/antora/billing/regle-pdf.html", UnreadReason.TOO_LARGE));
    }

    @Test
    void bornesDePagesEtDeDuree() {
        AntoraReader limited = new AntoraReader(ProxySettings.from(Map.of(), new Properties()), clock,
                new AntoraReader.Limits(10, Duration.ofMinutes(10), 2 * 1024 * 1024, Duration.ofSeconds(15), 4));
        Result r = limited.read(url(base() + "/antora/"));
        assertThat(r.pages().size() + r.unread().size() - 1).isEqualTo(10);
        assertThat(r.limitReached()).isTrue();

        // l'horloge dépasse 10 minutes dès la première page : la lecture s'arrête
        FixedClock late = new FixedClock();
        AntoraReader slow = new AntoraReader(ProxySettings.from(Map.of(), new Properties()), () -> {
            java.time.Instant now = late.now();
            late.advance(Duration.ofMinutes(6));
            return now;
        }, new AntoraReader.Limits(1_000, Duration.ofMinutes(10), 2 * 1024 * 1024, Duration.ofSeconds(15), 1));
        Result stopped = slow.read(url(base() + "/antora/"));
        assertThat(stopped.limitReached()).isTrue();
        assertThat(stopped.pages().size()).isLessThan(30);
    }

    @Test
    void sourceInjoignable() {
        Result r = reader.read(url("http://127.0.0.1:1/antora/"));
        assertThat(r.pages()).isEmpty();
        assertThat(r.unread()).containsExactly(new Unread("http://127.0.0.1:1/antora/", UnreadReason.UNREACHABLE));
    }

    @Test
    void proxyDEntrepriseApplique() {
        // docs.example.invalid n'existe pas : seule une requête passée par le proxy (WireMock) aboutit
        site.stubFor(get(urlPathEqualTo("/doc/sitemap.xml")).willReturn(aResponse().withHeader("Content-Type", "application/xml")
                .withBody("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"><url><loc>"
                        + "http://docs.example.invalid/doc/a.html</loc></url></urlset>")));
        site.stubFor(get(urlPathEqualTo("/doc/a.html")).willReturn(aResponse().withHeader("Content-Type", "text/html")
                .withBody("<html><body><article class=\"doc\"><h1 class=\"page\">A</h1><p>Par le proxy.</p></article></body></html>")));
        AntoraReader proxied = new AntoraReader(ProxySettings.from(Map.of("HTTP_PROXY", base()), new Properties()), clock,
                AntoraReader.Limits.DEFAULT);
        Result r = proxied.read(url("http://docs.example.invalid/doc/"));
        assertThat(r.pages()).singleElement().satisfies(p -> assertThat(p.text()).contains("Par le proxy."));
        site.verify(getRequestedFor(urlPathEqualTo("/doc/a.html")));
    }
}
