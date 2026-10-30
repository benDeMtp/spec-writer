import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  ApplicationConfig,
  SecurityContext,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { SANITIZE, provideMarkdown } from 'ngx-markdown';
import { routes } from './app.routes';
import { LaunchTokenService } from './core/launch/launch-token.service';
import { problemInterceptor } from './core/http/problem.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([problemInterceptor])),
    // Les descriptions Jira sont du contenu tiers : le HTML produit est toujours nettoyé.
    provideMarkdown({ sanitize: { provide: SANITIZE, useValue: SecurityContext.HTML } }),
    // Le jeton de lancement est échangé avant tout appel à l'API (research.md R14).
    provideAppInitializer(() => inject(LaunchTokenService).exchangeFromUrl()),
  ],
};
