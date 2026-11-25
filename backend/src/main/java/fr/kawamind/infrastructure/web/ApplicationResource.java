package fr.kawamind.infrastructure.web;

import fr.kawamind.application.lifecycle.GracefulStop;
import fr.kawamind.application.lifecycle.RunningWork;
import fr.kawamind.application.lifecycle.WorkKind;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /api/application} : état de l'outil et Quitter (019, contracts/rest-api.md). L'arrêt est
 * planifié après la réponse, pour que l'onglet sache qu'il a commencé.
 */
@Path("/api/application")
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationResource {

    public enum Os { MACOS, WINDOWS, LINUX }

    public enum Installation { INSTALLER, SOURCES }

    public record Work(WorkKind kind, String project, String conversation) {
    }

    public record State(Os os, Installation installation, List<Work> work) {
    }

    @Inject
    RunningWork running;

    @Inject
    GracefulStop stop;

    @GET
    public State state() {
        List<Work> work = running.items().stream()
                .map(i -> new Work(i.kind(), i.projectName(), i.conversationTitle())).toList();
        return new State(os(System.getProperty("os.name", "")),
                System.getProperty("jpackage.app-path") == null ? Installation.SOURCES : Installation.INSTALLER, work);
    }

    @POST
    @Path("/quit")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response quit() {
        if (!stop.stopping()) {
            Thread.ofVirtual().name("spec-writer-quit").start(stop::stop);
        }
        return Response.accepted(Map.of("stopping", true)).build();
    }

    static Os os(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("mac")) {
            return Os.MACOS;
        }
        return n.contains("win") ? Os.WINDOWS : Os.LINUX;
    }
}
