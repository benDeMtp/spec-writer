package fr.kawamind.infrastructure.jira;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import fr.kawamind.domain.jira.CreatedIssue;
import fr.kawamind.domain.jira.IssueTrackerException;
import fr.kawamind.domain.jira.IssueTrackerException.Kind;
import fr.kawamind.domain.jira.IssueType;
import fr.kawamind.domain.jira.JiraEndpoint;
import fr.kawamind.domain.jira.ParentIssue;
import fr.kawamind.domain.jira.Subtask;
import fr.kawamind.domain.jira.Transition;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** T010 (015, research R2) : sous-tâches, transitions et commentaires de Jira Server / Data Center. */
@QuarkusTest
@org.junit.jupiter.api.parallel.Isolated
class JiraTasksIT {

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
    void leTypeSousTacheEstLePremierDuProjetDansLOrdreDeJira() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/createmeta/FACT/issuetypes")).willReturn(okJson("""
                {"startAt":0,"maxResults":100,"total":4,"values":[
                {"id":"10001","name":"Story","subtask":false},
                {"id":"10007","name":"Sous-tâche technique","subtask":true},
                {"id":"10005","name":"Sous-tâche","subtask":true},
                {"id":"10002","name":"Bug","subtask":false}]}""")));
        assertThat(client.subtaskType(endpoint(), "FACT")).contains(new IssueType("10007", "Sous-tâche technique"));
        assertThat(client.issueTypes(endpoint(), "FACT")).extracting(IssueType::name).containsExactly("Story", "Bug");
    }

    @Test
    void sansTypeSousTacheAucunType() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/createmeta/FACT/issuetypes")).willReturn(okJson("""
                {"startAt":0,"maxResults":100,"total":1,"values":[{"id":"10001","name":"Story","subtask":false}]}""")));
        assertThat(client.subtaskType(endpoint(), "FACT")).isEqualTo(Optional.empty());
    }

    @Test
    void laStoryEstLueAvecSesSousTaches() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/FACT-120"))
                .withQueryParam("fields", equalTo("summary,status,issuetype,subtasks"))
                .willReturn(okJson("""
                        {"key":"FACT-120","fields":{"summary":"Rétractation",
                         "status":{"name":"En cours","statusCategory":{"key":"indeterminate"}},
                         "issuetype":{"name":"Story","subtask":false},
                         "subtasks":[
                          {"key":"FACT-121","fields":{"summary":"Maquette","status":{"name":"Terminé",
                           "statusCategory":{"key":"done"}}}},
                          {"key":"FACT-122","fields":{"summary":"Recette","status":{"name":"Ouvert",
                           "statusCategory":{"key":"new"}}}}]}}""")));
        ParentIssue story = client.parentIssue(endpoint(), "FACT-120");
        assertThat(story.subtask()).isFalse();
        assertThat(story.statusCategory()).isEqualTo("indeterminate");
        assertThat(story.subtasks()).containsExactly(new Subtask("FACT-121", "Maquette", "Terminé", "done"),
                new Subtask("FACT-122", "Recette", "Ouvert", "new"));
    }

    @Test
    void uneSousTacheEstCreeeSousSaStory() {
        jira.stubFor(post("/rest/api/2/issue").willReturn(okJson("{\"id\":\"30124\",\"key\":\"FACT-124\"}")));
        CreatedIssue created = client.createSubtask(endpoint(), "FACT", "FACT-120", "10005", "Exposer l'endpoint",
                "POST renvoie 201");
        assertThat(created.key()).isEqualTo("FACT-124");
        jira.verify(postRequestedFor(urlPathEqualTo("/rest/api/2/issue")).withRequestBody(equalToJson("""
                {"fields":{"project":{"key":"FACT"},"issuetype":{"id":"10005"},"parent":{"key":"FACT-120"},
                "summary":"Exposer l'endpoint","description":"POST renvoie 201"}}""")));
    }

    @Test
    void uneCreationRefuseeDonneLesCausesDeJira() {
        jira.stubFor(post("/rest/api/2/issue").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"errorMessages\":[],\"errors\":{\"parent\":\"Parent introuvable\"}}")));
        assertThatThrownBy(() -> client.createSubtask(endpoint(), "FACT", "FACT-120", "10005", "T", "R"))
                .isInstanceOfSatisfying(IssueTrackerException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.REJECTED);
                    assertThat(e.details()).containsExactly("parent : Parent introuvable");
                });
    }

    @Test
    void leResumeSeulEstRemplace() {
        jira.stubFor(put("/rest/api/2/issue/FACT-124").willReturn(aResponse().withStatus(204)));
        client.updateSummary(endpoint(), "FACT-124", "Nouveau titre");
        jira.verify(putRequestedFor(urlPathEqualTo("/rest/api/2/issue/FACT-124"))
                .withRequestBody(equalToJson("{\"fields\":{\"summary\":\"Nouveau titre\"}}")));
    }

    @Test
    void lesTransitionsSontLuesAvecLaCategorieDArriveePuisAppliquees() {
        jira.stubFor(get(urlPathEqualTo("/rest/api/2/issue/FACT-124/transitions")).willReturn(okJson("""
                {"transitions":[
                 {"id":"21","name":"Démarrer","to":{"name":"En cours","statusCategory":{"key":"indeterminate"}}},
                 {"id":"31","name":"Clore","to":{"name":"Fermé","statusCategory":{"key":"done"}}}]}""")));
        jira.stubFor(post("/rest/api/2/issue/FACT-124/transitions").willReturn(aResponse().withStatus(204)));
        assertThat(client.transitions(endpoint(), "FACT-124")).containsExactly(
                new Transition("21", "Démarrer", "indeterminate"), new Transition("31", "Clore", "done"));
        client.transition(endpoint(), "FACT-124", "21");
        jira.verify(postRequestedFor(urlPathEqualTo("/rest/api/2/issue/FACT-124/transitions"))
                .withRequestBody(matchingJsonPath("$.transition.id", equalTo("21"))));
    }

    @Test
    void uneTransitionRefuseeNEstPasUnePanne() {
        jira.stubFor(post("/rest/api/2/issue/FACT-124/transitions").willReturn(aResponse().withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"errorMessages\":[\"Champ Résolution requis\"],\"errors\":{}}")));
        assertThatThrownBy(() -> client.transition(endpoint(), "FACT-124", "31"))
                .isInstanceOfSatisfying(IssueTrackerException.class, e -> {
                    assertThat(e.kind()).isEqualTo(Kind.REJECTED);
                    assertThat(e.details()).containsExactly("Champ Résolution requis");
                });
    }

    @Test
    void unCommentaireEstAjouteEnWiki() {
        jira.stubFor(post("/rest/api/2/issue/FACT-124/comment").willReturn(okJson("{\"id\":\"1\"}")));
        client.addComment(endpoint(), "FACT-124", "Compte rendu de claude-code : fait");
        jira.verify(postRequestedFor(urlPathEqualTo("/rest/api/2/issue/FACT-124/comment"))
                .withRequestBody(equalToJson("{\"body\":\"Compte rendu de claude-code : fait\"}")));
    }

    @Test
    void jiraEnPanneEstInjoignable() {
        jira.stubFor(post("/rest/api/2/issue/FACT-124/comment").willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> client.addComment(endpoint(), "FACT-124", "x"))
                .isInstanceOfSatisfying(IssueTrackerException.class,
                        e -> assertThat(e.kind()).isEqualTo(Kind.UNREACHABLE));
    }
}
