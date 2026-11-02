package dev.specwriter.infrastructure.jira;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import dev.specwriter.domain.jira.CreatedIssue;
import dev.specwriter.domain.jira.IssueTrackerException;
import dev.specwriter.domain.jira.IssueType;
import dev.specwriter.domain.jira.JiraEndpoint;
import dev.specwriter.domain.jira.RequiredField;
import dev.specwriter.domain.jira.RequiredField.Kind;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T125 : types de ticket, champs obligatoires et création (Jira Data Center ≥ 8.4). */
@QuarkusTest
class JiraCreateMetaIT {

    static WireMockServer jira;

    @Inject
    JiraRestClient client;

    @BeforeAll
    static void start() {
        jira = new WireMockServer(options().bindAddress("127.0.0.1").dynamicPort());
        jira.start();
    }

    @AfterAll
    static void stop() {
        jira.stop();
    }

    @BeforeEach
    void reset() {
        jira.resetAll();
    }

    static JiraEndpoint endpoint() {
        return new JiraEndpoint("http://127.0.0.1:" + jira.port(), "TEST-JIRA-PAT", null);
    }

    @Test
    void typesDeTicketSansSousTaches() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/createmeta/PROJ/issuetypes")).willReturn(okJson("""
                {"startAt":0,"maxResults":50,"total":3,"values":[
                 {"id":"10001","name":"Story","subtask":false},
                 {"id":"10002","name":"Bug","subtask":false},
                 {"id":"10003","name":"Sous-tâche","subtask":true}]}""")));
        assertThat(client.issueTypes(endpoint(), "PROJ"))
                .containsExactly(new IssueType("10001", "Story"), new IssueType("10002", "Bug"));
    }

    @Test
    void champsObligatoiresSansValeurParDefaut() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/createmeta/PROJ/issuetypes/10001")).willReturn(okJson("""
                {"values":[
                 {"fieldId":"summary","name":"Résumé","required":true,"hasDefaultValue":false,"schema":{"type":"string"}},
                 {"fieldId":"priority","name":"Priorité","required":true,"hasDefaultValue":true,"schema":{"type":"priority"}},
                 {"fieldId":"labels","name":"Étiquettes","required":false,"hasDefaultValue":false,"schema":{"type":"array"}},
                 {"fieldId":"customfield_10100","name":"Équipe","required":true,"hasDefaultValue":false,
                  "schema":{"type":"option","custom":"select"},"allowedValues":[{"id":"1","value":"Alpha"},{"id":"2","value":"Beta"}]},
                 {"fieldId":"customfield_10101","name":"Points","required":true,"hasDefaultValue":false,"schema":{"type":"number"}},
                 {"fieldId":"customfield_10102","name":"Échéance","required":true,"hasDefaultValue":false,"schema":{"type":"date"}},
                 {"fieldId":"fixVersions","name":"Versions","required":true,"hasDefaultValue":false,
                  "schema":{"type":"array","items":"version"},"allowedValues":[{"id":"7","name":"1.0"}]},
                 {"fieldId":"customfield_10103","name":"Responsable","required":true,"hasDefaultValue":false,"schema":{"type":"user"}}
                ]}""")));
        List<RequiredField> fields = client.requiredFields(endpoint(), "PROJ", "10001");
        assertThat(fields).extracting(RequiredField::id).containsExactly("summary", "customfield_10100", "customfield_10101",
                "customfield_10102", "fixVersions", "customfield_10103");
        assertThat(fields).extracting(RequiredField::kind).containsExactly(Kind.TEXT, Kind.OPTION, Kind.NUMBER, Kind.DATE,
                Kind.OPTIONS, Kind.UNSUPPORTED);
        assertThat(fields.get(1).options()).containsExactly(new RequiredField.Option("1", "Alpha"),
                new RequiredField.Option("2", "Beta"));
        assertThat(fields.get(4).options()).containsExactly(new RequiredField.Option("7", "1.0"));
    }

    @Test
    void creationDuTicket() {
        jira.stubFor(post("/rest/api/2/issue").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"20001\",\"key\":\"PROJ-42\",\"self\":\"http://jira/rest/api/2/issue/20001\"}")));
        CreatedIssue created = client.createIssue(endpoint(), "PROJ", "10001", "Réservation", "h1. Réservation",
                Map.of("customfield_10100", Map.of("id", "1")));
        assertThat(created).isEqualTo(new CreatedIssue("20001", "PROJ-42"));
        jira.verify(postRequestedFor(urlPathEqualTo("/rest/api/2/issue")).withRequestBody(equalToJson("""
                {"fields":{"project":{"key":"PROJ"},"issuetype":{"id":"10001"},"summary":"Réservation",
                 "description":"h1. Réservation","customfield_10100":{"id":"1"}}}""")));
    }

    @Test
    void refusAvecLesChampsEnCause() {
        jira.stubFor(post("/rest/api/2/issue").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"errorMessages":["Le projet est archivé"],
                         "errors":{"customfield_10100":"Équipe est obligatoire.","components":"Composant inconnu"}}""")));
        assertThatThrownBy(() -> client.createIssue(endpoint(), "PROJ", "10001", "T", "d", Map.of()))
                .isInstanceOfSatisfying(IssueTrackerException.class, e -> {
                    assertThat(e.kind()).isEqualTo(IssueTrackerException.Kind.REJECTED);
                    assertThat(e.details()).containsExactlyInAnyOrder("Le projet est archivé",
                            "customfield_10100 : Équipe est obligatoire.", "components : Composant inconnu");
                    DomainException d = e.toDomain();
                    assertThat(d.code()).isEqualTo(ErrorCode.JIRA_REJECTED);
                    assertThat(d.userMessage()).contains("Équipe est obligatoire.").contains("Composant inconnu");
                });
    }

    @Test
    void droitsInsuffisants() {
        jira.stubFor(post("/rest/api/2/issue").willReturn(aResponse().withStatus(403)));
        assertThatThrownBy(() -> client.createIssue(endpoint(), "PROJ", "10001", "T", "d", Map.of()))
                .isInstanceOfSatisfying(IssueTrackerException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.JIRA_PERMISSION_DENIED));
    }
}
