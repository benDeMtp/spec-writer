package dev.specwriter.infrastructure.web;

import dev.specwriter.application.settings.ManageBrowser;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * {@code /api/settings/browser} (013, T057) : navigateur où s'ouvre spec-writer, et ouverture
 * immédiate dans ce navigateur. Réservé à une session ouverte (LocalSecurityFilter) : le nouveau
 * jeton n'est jamais renvoyé ni écrit dans le journal, il ne part que vers le navigateur lancé.
 */
@Path("/api/settings/browser")
@Produces(MediaType.APPLICATION_JSON)
public class BrowserSettingsResource {

    public record BrowserInput(String browserId) {
    }

    @Inject
    ManageBrowser browser;

    @Inject
    LaunchTokenService tokens;

    @ConfigProperty(name = "quarkus.http.port")
    int port;

    @GET
    public ManageBrowser.View get() {
        return browser.get();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public ManageBrowser.View save(BrowserInput input) {
        return browser.save(input == null ? null : input.browserId());
    }

    @POST
    @Path("/open")
    public ManageBrowser.Opened open() {
        return browser.open(BrowserOpener.launchUrl(port, tokens.issue()).toString());
    }
}
