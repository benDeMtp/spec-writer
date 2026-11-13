package dev.specwriter.application.sources;

import dev.specwriter.domain.knowledge.ArchElement;
import dev.specwriter.domain.knowledge.ArchRelation;
import dev.specwriter.domain.knowledge.ArchitectureModel;
import dev.specwriter.domain.sources.Module;
import dev.specwriter.domain.sources.ModuleId;
import dev.specwriter.domain.sources.ModuleKind;
import dev.specwriter.domain.sources.PerimeterAnalysis;
import dev.specwriter.domain.sources.PerimeterModule;
import dev.specwriter.domain.sources.Reason;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Propagation par le modèle d'architecture (013, US4, research.md R8) : les éléments reliés à un
 * module trouvé par un terme ajoutent leur module « à vérifier », ou figurent à part s'ils n'en ont
 * pas. Un seul saut : les relations du module ajouté ne sont pas suivies.
 */
final class ArchitecturePerimeter {

    /** Sortes de modules qui ont leur place dans le modèle ; le pseudo-module de la racine n'en a pas. */
    private static final Set<ModuleKind> MODELLED = Set.of(ModuleKind.MAVEN, ModuleKind.NPM, ModuleKind.HELM);

    record Result(List<PerimeterModule> modules, List<PerimeterAnalysis.ArchLink> architectureOnly) {
    }

    private final ArchitectureModel model;
    /** Module → élément, et élément → premier module en ordre d'identifiant. */
    private final Map<ModuleId, ArchElement> elementOf = new HashMap<>();
    private final Map<String, Module> moduleOf = new HashMap<>();

    private ArchitecturePerimeter(ArchitectureModel model, List<Module> known) {
        this.model = model;
        Map<String, ArchElement> byHint = new HashMap<>();
        Map<String, ArchElement> byName = new HashMap<>();
        for (ArchElement e : model.elements()) {
            if (e.moduleHint() != null) {
                byHint.putIfAbsent(e.moduleHint(), e);
            } else if (e.type() == ArchElement.Type.CONTAINER || e.type() == ArchElement.Type.COMPONENT) {
                byName.putIfAbsent(normalized(e.shortName()), e);
            }
        }
        Map<ModuleId, Module> sorted = new TreeMap<>();
        known.forEach(m -> sorted.put(m.id(), m));
        for (Module m : sorted.values()) {
            if (!MODELLED.contains(m.kind())) {
                continue;
            }
            ArchElement e = byHint.getOrDefault(m.path(), byHint.getOrDefault(m.name(), byName.get(normalized(m.name()))));
            if (e != null) {
                elementOf.put(m.id(), e);
                moduleOf.putIfAbsent(e.name(), m);
            }
        }
    }

    /** Sans modèle d'architecture, les modules restent tels quels : aucun n'est dit absent du modèle. */
    static Result apply(ArchitectureModel model, List<Module> known, List<PerimeterModule> modules) {
        if (model == null || model.elements().isEmpty()) {
            return new Result(modules, List.of());
        }
        return new ArchitecturePerimeter(model, known).propagate(modules);
    }

    private Result propagate(List<PerimeterModule> modules) {
        Map<ModuleId, PerimeterModule> byId = new LinkedHashMap<>();
        modules.forEach(m -> byId.put(m.module().id(), m));
        List<PerimeterAnalysis.ArchLink> only = new ArrayList<>();
        for (PerimeterModule found : modules) {
            ArchElement own = found.direct() ? elementOf.get(found.module().id()) : null;
            if (own == null) {
                continue;
            }
            for (ArchRelation r : model.relations()) {
                String other = ArchElement.within(r.source(), own.name()) ? r.destination()
                        : ArchElement.within(r.destination(), own.name()) ? r.source() : null;
                if (other == null || ArchElement.within(other, own.name())) {
                    continue;
                }
                Module related = module(other);
                Reason.Architecture reason = new Reason.Architecture(shortName(other), own.shortName(), r.description(),
                        r.technology());
                if (related == null) {
                    PerimeterAnalysis.ArchLink link = new PerimeterAnalysis.ArchLink(reason.element(), reason.related(),
                            reason.description(), reason.technology());
                    if (!only.contains(link)) {
                        only.add(link);
                    }
                } else if (!related.id().equals(found.module().id())) {
                    PerimeterModule existing = byId.get(related.id());
                    if (existing == null) {
                        byId.put(related.id(), PerimeterModule.found(related, List.of(reason), 0, List.of()));
                    } else if (!existing.reasons().contains(reason)) {
                        byId.put(related.id(), existing.withReasons(List.of(reason)));
                    }
                }
            }
        }
        List<PerimeterModule> out = new ArrayList<>();
        for (PerimeterModule m : byId.values()) {
            ArchElement e = elementOf.get(m.module().id());
            if (e != null) {
                out.add(m.inArchitecture(e.name(), PerimeterModule.ArchStatus.MATCHED));
            } else {
                out.add(MODELLED.contains(m.module().kind()) ? m.inArchitecture(null, PerimeterModule.ArchStatus.ABSENT) : m);
            }
        }
        return new Result(out, List.copyOf(only));
    }

    /** Module de l'élément, ou du plus proche de ses ancêtres qui en a un (composant d'un conteneur). */
    private Module module(String element) {
        for (String name = element; ; name = name.substring(0, name.lastIndexOf('/'))) {
            Module m = moduleOf.get(name);
            if (m != null || name.indexOf('/') < 0) {
                return m;
            }
        }
    }

    private static String shortName(String canonical) {
        return canonical.substring(canonical.lastIndexOf('/') + 1);
    }

    /** Minuscules, sans {@code -}, {@code _}, {@code .} ni espace (research.md R8). */
    static String normalized(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[-_.\\s]", "");
    }
}
