package dev.specwriter.infrastructure.github;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Page d'autorisation simulée des tests E2E ; 404 hors du mode GitHub simulé (T033). */
@Path("/e2e/fake-github-authorize")
public class FakeGitHubAuthorizePage {

    @ConfigProperty(name = "specwriter.github.fake", defaultValue = "false")
    boolean fake;

    @GET
    @Produces(MediaType.TEXT_HTML)
    public String page() {
        if (!fake) {
            throw new NotFoundException();
        }
        return "<!doctype html><html lang=\"fr\"><head><title>Autorisation GitHub simulée</title></head>"
                + "<body><main><h1>Autorisation GitHub simulée</h1><p>Vous pouvez fermer cet onglet.</p></main></body></html>";
    }
}
