package dev.specwriter.domain.sources;

/** Module nommé dans une raison : identité et nom affiché. */
public record ModuleRef(ModuleId id, String name) {

    public static ModuleRef of(Module module) {
        return new ModuleRef(module.id(), module.name());
    }
}
