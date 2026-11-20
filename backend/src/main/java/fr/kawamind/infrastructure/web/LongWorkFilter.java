package fr.kawamind.infrastructure.web;

import fr.kawamind.application.lifecycle.LongWorkTracker;
import fr.kawamind.application.lifecycle.WorkKind;
import fr.kawamind.domain.port.ProjectRepository;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Compte les routes {@link LongWork} pendant leur traitement (019, research.md R4). */
@Provider
@LongWork
public class LongWorkFilter implements ContainerRequestFilter, ContainerResponseFilter {

    static final String TICKET = LongWorkFilter.class.getName() + ".ticket";

    private final LongWorkTracker tracker;
    private final ProjectRepository projects;

    @Inject
    public LongWorkFilter(LongWorkTracker tracker, ProjectRepository projects) {
        this.tracker = tracker;
        this.projects = projects;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        request.setProperty(TICKET, tracker.begin(WorkKind.GENERATION, projects.currentProjectId().orElse(null)));
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (request.getProperty(TICKET) instanceof LongWorkTracker.Ticket ticket) {
            request.removeProperty(TICKET);
            tracker.end(ticket);
        }
    }
}
