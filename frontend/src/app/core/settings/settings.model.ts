/** Configuration des services, telle que renvoyée par l'API (contracts/openapi.yaml, Settings). */
export type CopilotState = 'NOT_CONFIGURED' | 'CONNECTED' | 'NO_COPILOT' | 'EXPIRED';
export type FlowState = 'PENDING' | 'CONNECTED' | 'EXPIRED' | 'DENIED' | 'NO_COPILOT' | 'ERROR';
export type SecretPersistence = 'KEYCHAIN' | 'MEMORY_ONLY';
export type ConsentService = 'COPILOT' | 'JIRA';
export type JiraTestResult =
  | 'OK'
  | 'UNREACHABLE'
  | 'TOKEN_REJECTED'
  | 'PROJECT_NOT_FOUND'
  | 'MISSING_PERMISSION'
  | 'UNSUPPORTED_VERSION'
  | 'CONSENT_REQUIRED';

export interface CopilotSettings {
  state: CopilotState;
  login: string | null;
  secretPersistence: SecretPersistence;
  consent: boolean;
  model: string | null;
  availableModels: string[];
}

export interface JiraSettings {
  enabled: boolean;
  serverUrl: string;
  defaultProjectKey: string;
  caCertificatePath: string | null;
  tokenSet: boolean;
  secretPersistence: SecretPersistence;
  insecureHttp: boolean;
  lastTest: { at: string; result: JiraTestResult } | null;
  consent: boolean;
}

export interface Settings {
  copilot: CopilotSettings;
  jira: JiraSettings | null;
}

export interface ConnectionStart {
  flowId: string;
  authorizationUrl: string;
  requestedScopes: string[];
  expiresAt: string;
}

export interface ConnectionStatus {
  state: FlowState;
  login: string | null;
  secretPersistence: SecretPersistence;
}

export interface JiraInput {
  serverUrl: string;
  defaultProjectKey: string;
  token?: string;
  caCertificatePath?: string | null;
  acceptInsecureHttp: boolean;
}

export interface JiraTestOutcome {
  result: JiraTestResult;
  serverVersion: string | null;
  userMessage: string;
}
