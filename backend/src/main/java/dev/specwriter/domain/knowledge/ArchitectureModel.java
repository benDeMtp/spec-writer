package dev.specwriter.domain.knowledge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Modèle d'architecture d'un projet : éléments, relations et déploiements lus dans le workspace
 * Structurizr (FR-038 à FR-041, research.md R22). Les flux et les constats de déploiement sont
 * calculés ici, jamais devinés par l'IA.
 */
public record ArchitectureModel(List<ArchElement> elements, List<ArchRelation> relations,
        List<ArchDeployment> deployments) {

    public static final ArchitectureModel EMPTY = new ArchitectureModel(List.of(), List.of(), List.of());

    private static final Set<String> TRANSPORT_TAGS = Set.of("topic", "queue", "bus");
    private static final Pattern TRANSPORT_TECHNOLOGY = Pattern.compile("\\b(kafka|amqp|jms|mq)\\b");

    public ArchitectureModel {
        elements = List.copyOf(elements);
        relations = List.copyOf(relations);
        deployments = List.copyOf(deployments);
    }

    /** Existence d'un échange entre deux modules : direct, par un seul intermédiaire de transport, ou absent. */
    public record Exchange(String source, String destination, Kind kind, String via) {
        public enum Kind {
            DIRECT,
            INDIRECT,
            MISSING
        }

        public static Exchange direct(String source, String destination) {
            return new Exchange(source, destination, Kind.DIRECT, null);
        }
    }

    /** Constat de production, inséré tel quel dans « Efforts supplémentaires (déploiement) ». */
    public record DeploymentFact(Kind kind, String element, String other) {
        public enum Kind {
            NOT_DEPLOYED,
            MULTIPLE_INSTANCES,
            CROSS_INFRASTRUCTURE,
            SHARED_INFRASTRUCTURE
        }
    }

    /** Intermédiaire de transport : tag ou archétype {@code topic}, {@code queue}, {@code bus}, ou technologie de messagerie. */
    public static boolean isTransport(Collection<String> tags, String technology) {
        if (tags != null && tags.stream().anyMatch(t -> TRANSPORT_TAGS.contains(t.strip().toLowerCase(Locale.ROOT)))) {
            return true;
        }
        return technology != null && TRANSPORT_TECHNOLOGY.matcher(technology.toLowerCase(Locale.ROOT)).find();
    }

    /** Environnement de production : son nom contient {@code prod}, sans tenir compte de la casse. */
    public static boolean isProduction(String environment) {
        return environment != null && environment.toLowerCase(Locale.ROOT).contains("prod");
    }

    public Optional<ArchElement> element(String name) {
        return elements.stream().filter(e -> e.name().equals(name)).findFirst();
    }

    public Exchange exchange(String a, String b) {
        if (relations.stream().anyMatch(r -> r.links(a, b))) {
            return Exchange.direct(a, b);
        }
        for (ArchElement transport : elements) {
            String t = transport.name();
            if (!transport.transport() || ArchElement.within(t, a) || ArchElement.within(t, b)
                    || ArchElement.within(a, t) || ArchElement.within(b, t)) {
                continue;
            }
            if (relations.stream().anyMatch(r -> r.links(a, t)) && relations.stream().anyMatch(r -> r.links(t, b))) {
                return new Exchange(a, b, Exchange.Kind.INDIRECT, t);
            }
        }
        return new Exchange(a, b, Exchange.Kind.MISSING, null);
    }

    /**
     * Constats de production pour les modules impactés et les échanges qu'ils supposent (paires
     * source, destination).
     */
    public List<DeploymentFact> deploymentFacts(List<String> modules, List<List<String>> exchanges) {
        Set<DeploymentFact> facts = new LinkedHashSet<>();
        for (String module : modules) {
            Optional<ArchElement> element = element(module);
            if (element.isPresent() && element.get().type() == ArchElement.Type.PERSON) {
                continue;
            }
            List<ArchDeployment> production = production(module);
            boolean own = element.map(e -> e.scope() == ArchScope.PROJECT).orElse(false);
            if (production.isEmpty()) {
                if (own) {
                    facts.add(new DeploymentFact(DeploymentFact.Kind.NOT_DEPLOYED, module, null));
                }
                continue;
            }
            if (production.stream().mapToInt(ArchDeployment::instances).sum() > 1) {
                facts.add(new DeploymentFact(DeploymentFact.Kind.MULTIPLE_INSTANCES, module, null));
            }
            if (own && (insideCommonSystem(module) || production.stream().anyMatch(ArchDeployment::sharedInfrastructure))) {
                facts.add(new DeploymentFact(DeploymentFact.Kind.SHARED_INFRASTRUCTURE, module, null));
            }
        }
        for (List<String> pair : exchanges) {
            if (pair.size() != 2) {
                continue;
            }
            Set<String> left = roots(pair.get(0));
            Set<String> right = roots(pair.get(1));
            if (!left.isEmpty() && !right.isEmpty() && left.stream().noneMatch(right::contains)) {
                facts.add(new DeploymentFact(DeploymentFact.Kind.CROSS_INFRASTRUCTURE, pair.get(0), pair.get(1)));
            }
        }
        return new ArrayList<>(facts);
    }

    /** Instances de production du module, de ses descendants ou de l'élément qui le contient. */
    private List<ArchDeployment> production(String module) {
        return deployments.stream().filter(d -> isProduction(d.environment()))
                .filter(d -> ArchElement.within(d.element(), module) || ArchElement.within(module, d.element()))
                .toList();
    }

    private Set<String> roots(String module) {
        Set<String> roots = new LinkedHashSet<>();
        production(module).forEach(d -> roots.add(d.rootNode()));
        return roots;
    }

    private boolean insideCommonSystem(String module) {
        int slash = module.indexOf('/');
        return slash > 0 && element(module.substring(0, slash)).map(e -> e.scope() == ArchScope.COMMON).orElse(false);
    }
}
