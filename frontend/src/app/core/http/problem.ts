/** Erreur renvoyée par l'API (contracts/openapi.yaml, Problem). */
export type ProblemAction = 'RETRY' | 'OPEN_SETTINGS' | 'RECONNECT' | 'EDIT_INPUT' | 'NONE';

export interface Problem {
  code: string;
  userMessage: string;
  action: ProblemAction;
  status?: number;
}

export function isProblem(body: unknown): body is Problem {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as Problem).code === 'string' &&
    typeof (body as Problem).userMessage === 'string'
  );
}
