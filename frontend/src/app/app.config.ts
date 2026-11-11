import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  SecurityContext,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { TitleStrategy, provideRouter, withComponentInputBinding } from '@angular/router';
import { MARKED_OPTIONS, SANITIZE, provideMarkdown } from 'ngx-markdown';
import { routes } from './app.routes';
import { LaunchTokenService } from './core/launch/launch-token.service';
import { problemInterceptor } from './core/http/problem.interceptor';
import { ProjectTitleStrategy } from './core/navigation/project-title.strategy';
import { markedOptions } from './shared/ui/markdown-options';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // 012 : le titre de l'onglet nomme le projet courant pour les pages du bloc « Projet »
    { provide: TitleStrategy, useClass: ProjectTitleStrategy },
    provideHttpClient(withInterceptors([problemInterceptor])),
    // Les descriptions Jira sont du contenu tiers : liens bornés (FR-043a), HTML toujours nettoyé.
    provideMarkdown({
      markedOptions: { provide: MARKED_OPTIONS, useFactory: markedOptions },
      sanitize: { provide: SANITIZE, useValue: SecurityContext.HTML },
    }),
    // Le jeton de lancement est échangé avant tout appel à l'API (research.md R14).
    provideAppInitializer(() => inject(LaunchTokenService).exchangeFromUrl()),
  ],
};
