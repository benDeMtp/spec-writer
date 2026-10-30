/** Sessions, questions et versions, telles que renvoyées par l'API (contracts/openapi.yaml). */
export type SessionState = 'QUESTIONING' | 'READY_TO_GENERATE' | 'GENERATED';
export type QuestionState = 'PENDING' | 'ANSWERED' | 'SKIPPED';
export type Language = 'fr' | 'en';

export interface Question {
  id: string;
  theme: string;
  label: string;
  rationale: string;
  suggestions: string[];
  defaultValue: string | null;
  state: QuestionState;
  answer: string | null;
}

export interface SessionSummary {
  id: string;
  name: string;
  state: SessionState;
  updatedAt: string;
  latestVersion: number | null;
  jiraKey: string | null;
  /** Faux si la sauvegarde est endommagée : la session ne peut plus être ouverte. */
  readable: boolean;
}

export interface SessionDetail extends SessionSummary {
  need: string;
  language: Language;
  progress: { answered: number; skipped: number; total: number };
  currentBatch: Question[];
  answeredQuestions: Question[];
  warnings: string[];
  /** Effet de la dernière réponse modifiée (FR-007). */
  revision?: { removed: string[]; added: string[] };
}

export interface VersionSummary {
  number: number;
  origin: 'GENERATED' | 'AI_EDIT' | 'JIRA_IMPORT' | 'CONFLICT_MERGE';
  createdAt: string;
  pushedToJira: boolean;
}

export interface SourceRef {
  kind: 'NEED_EXCERPT' | 'ANSWER' | 'ASSUMPTION';
  questionId: string | null;
  excerpt: string;
}

export interface UseCase {
  code: string;
  title: string;
  actor: string;
  goal: string;
  preconditions: string[];
  nominalFlow: string[];
  alternatives: string[];
  source: SourceRef | null;
}

export interface Scenario {
  code: string;
  useCaseCode: string;
  title: string;
  kind: 'NOMINAL' | 'ERROR' | 'EDGE';
  asA: string;
  given: string[];
  when: string[];
  then: string[];
  assumption: boolean;
}

export interface Assumption {
  code: string;
  statement: string;
  origin: 'SKIPPED_QUESTION' | 'TOOL_INFERENCE';
  questionId: string | null;
}

export interface Requirement {
  code: string;
  category: 'FUNCTIONAL' | 'TECHNICAL';
  statement: string;
}

export interface Specification {
  number: number;
  origin: string;
  createdAt: string;
  title: string;
  summary: string;
  useCases: UseCase[];
  scenarios: Scenario[];
  assumptions: Assumption[];
  requirements: Requirement[];
  markdown: string;
}
