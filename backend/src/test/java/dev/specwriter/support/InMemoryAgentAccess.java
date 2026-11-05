package dev.specwriter.support;

import dev.specwriter.domain.agent.AgentAccess;
import dev.specwriter.domain.port.AgentAccessRepository;
import dev.specwriter.domain.port.AgentEndpoint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Réglage, journal et fichier des agents en mémoire. */
public class InMemoryAgentAccess implements AgentAccessRepository, AgentEndpoint {

    public boolean enabled;
    public final List<AgentAccess> entries = new ArrayList<>();
    public final List<String> endpointCalls = new ArrayList<>();

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void record(AgentAccess access) {
        entries.add(access);
    }

    @Override
    public List<Line> page(int startAt, int max) {
        return entries.stream().sorted(Comparator.comparing(AgentAccess::at).reversed()).skip(startAt).limit(max)
                .map(a -> new Line(a.at(), a.agentName(), a.operation(), null, null, a.oldStatus(), a.newStatus())).toList();
    }

    @Override
    public int count() {
        return entries.size();
    }

    @Override
    public int purgeBefore(Instant limit) {
        int before = entries.size();
        entries.removeIf(a -> a.at().isBefore(limit));
        return before - entries.size();
    }

    @Override
    public void publish() {
        endpointCalls.add("publish");
    }

    @Override
    public void withdraw() {
        endpointCalls.add("withdraw");
    }

    @Override
    public void regenerate() {
        endpointCalls.add("regenerate");
    }
}
