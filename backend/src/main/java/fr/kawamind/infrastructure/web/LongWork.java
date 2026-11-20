package fr.kawamind.infrastructure.web;

import fr.kawamind.application.lifecycle.WorkKind;
import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Route synchrone qui dure (une génération) : comptée comme travail en cours tant qu'elle n'a pas
 * répondu, pour être nommée avant Quitter (019, research.md R4).
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD })
public @interface LongWork {

    WorkKind value() default WorkKind.GENERATION;
}
