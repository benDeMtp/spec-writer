package fr.kawamind.infrastructure.web;

import fr.kawamind.application.settings.ConfluenceAccess;
import fr.kawamind.application.settings.DeleteConfluenceSettings;
import fr.kawamind.application.settings.SaveConfluenceSettings;
import fr.kawamind.application.settings.TestConfluenceConnection;
import fr.kawamind.domain.error.DomainException;
import fr.kawamind.domain.error.ErrorCode;
import fr.kawamind.domain.settings.ConfluenceSettings;
import fr.kawamind.domain.settings.SecretPersistence;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;

/** {@code /api/settings/confluence} (016, contracts/rest-api.md). Le jeton n'est jamais renvoyé. */
@Path("/api/settings/confluence")
@Produces(MediaType.APPLICATION_JSON)
public class ConfluenceSettingsResource {

    public record LastTestView(Instant at, String result) {
    }

    public record ConfluenceView(boolean enabled, String serverUrl, String caCertificatePath, boolean insecureHttp,
            String serverVersion, LastTestView lastTest, boolean tokenSet, SecretPersistence secretPersistence) {

        static final ConfluenceView NONE = new ConfluenceView(false, null, null, false, null, null, false, null);
    }

    public record ConfluenceRequest(String serverUrl, String caCertificatePath, boolean acceptInsecureHttp,
            String token) {

        @Override
        public String toString() {
            return "ConfluenceRequest[serverUrl=" + serverUrl + ", token=" + (token == null ? "absent" : "***") + "]";
        }
    }

    public record TestView(String result, String account, String serverVersion, String message) {
    }

    @Inject
    ConfluenceAccess access;
    @Inject
    SaveConfluenceSettings save;
    @Inject
    DeleteConfluenceSettings delete;
    @Inject
    TestConfluenceConnection test;

    @GET
    public ConfluenceView get() {
        return access.settings().map(this::view).orElse(ConfluenceView.NONE);
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public ConfluenceView save(ConfluenceRequest request) {
        if (request == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED);
        }
        return view(save.run(new SaveConfluenceSettings.Command(request.serverUrl(), request.token(),
                request.caCertificatePath(), request.acceptInsecureHttp())));
    }

    @DELETE
    public void delete() {
        delete.run();
    }

    @POST
    @Path("/test")
    public TestView test() {
        TestConfluenceConnection.Outcome outcome = test.run();
        return new TestView(outcome.result().name(), outcome.account(), outcome.serverVersion(), outcome.message());
    }

    private ConfluenceView view(ConfluenceSettings s) {
        LastTestView last = s.lastTest() == null ? null
                : new LastTestView(s.lastTest().at(), s.lastTest().result().name());
        return new ConfluenceView(true, s.serverUrl(), s.caCertificatePath(), s.insecureHttp(), s.serverVersion(), last,
                save.tokenSet(), s.secretPersistence());
    }
}
