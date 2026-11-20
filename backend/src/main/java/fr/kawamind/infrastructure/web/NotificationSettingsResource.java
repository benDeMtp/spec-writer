package fr.kawamind.infrastructure.web;

import fr.kawamind.application.settings.ManageNotifications;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** {@code /api/settings/notifications} : notifications des sources mises à jour (017, FR-016). */
@Path("/api/settings/notifications")
@Produces(MediaType.APPLICATION_JSON)
public class NotificationSettingsResource {

    public record NotificationsInput(boolean sourceUpdates) {
    }

    @Inject
    ManageNotifications manage;

    @GET
    public ManageNotifications.View get() {
        return manage.get();
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public ManageNotifications.View save(NotificationsInput input) {
        return manage.save(input != null && input.sourceUpdates());
    }
}
