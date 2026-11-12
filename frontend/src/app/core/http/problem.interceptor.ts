import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { LaunchTokenService } from '../launch/launch-token.service';
import { Problem, isProblem } from './problem';
import { ProblemStore } from './problem.store';

const NETWORK_PROBLEM: Problem = {
  code: 'NETWORK',
  userMessage: "spec-writer ne répond pas. Vérifiez qu'il est toujours lancé, puis réessayez.",
  action: 'RETRY',
};

/**
 * Affiche toute erreur de l'API dans le bandeau commun, avec son message métier et son action.
 * Les requêtes marquées par l'en-tête X-Sw-Silent gèrent leurs erreurs elles-mêmes.
 */
export const problemInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(ProblemStore);
  const launch = inject(LaunchTokenService);
  const silent = req.headers.has('X-Sw-Silent');
  const request = silent ? req.clone({ headers: req.headers.delete('X-Sw-Silent') }) : req;
  return next(request).pipe(
    catchError((error: unknown) => {
      // 012 (FR-018) : la fenêtre n'est plus reliée à spec-writer, même pour une requête silencieuse ;
      // l'écran de relance remplace l'application, sans bandeau
      if (error instanceof HttpErrorResponse && isProblem(error.error) && error.error.code === 'SESSION_REQUIRED') {
        launch.state.set('rejected');
        return throwError(() => error);
      }
      if (!silent && error instanceof HttpErrorResponse) {
        if (isProblem(error.error)) {
          store.show({ ...error.error, status: error.status });
        } else if (error.status === 0) {
          store.show(NETWORK_PROBLEM);
        }
      }
      return throwError(() => error);
    }),
  );
};
