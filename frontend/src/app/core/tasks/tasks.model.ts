/** Tâches d'une spécification (015, contracts/rest-api.md). */
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'MISSING';
export type BasisKind = 'MODULE' | 'USE_CASE' | 'NONE';

export interface TaskBasis {
  kind: BasisKind;
  ref: string | null;
}

export interface SpecTask {
  id: string;
  position: number;
  title: string;
  outcome: string;
  basis: TaskBasis;
  origin: 'PROPOSED' | 'MANUAL' | 'JIRA';
  creation: 'NOT_CREATED' | 'CREATED';
  key: string | null;
  url: string | null;
  status: TaskStatus;
  jiraStatusName: string | null;
  heldBy: string | null;
  heldSince: string | null;
  holderClosed: boolean;
  /** Transition enregistrée mais pas encore dans Jira (« à synchroniser »). */
  pending: Exclude<TaskStatus, 'MISSING'> | null;
  report: string | null;
  dependsOn: string[];
}

export interface TasksView {
  linkedIssueKey: string | null;
  canPropose: boolean;
  proposeBlocked: 'NO_LINKED_TICKET' | 'PARENT_IS_SUBTASK' | null;
  tracking: 'ACTIVE' | 'SUSPENDED';
  progress: { done: number; total: number };
  lastReadAt: string | null;
  readProblem: 'JIRA_UNREACHABLE' | null;
  pendingSync: number;
  creationProblem: string | null;
  hasProposal: boolean;
  tasks: SpecTask[];
}

export interface ProposedTask {
  title: string;
  outcome: string;
  basis: TaskBasis;
  /** Positions, dans la proposition, des tâches préalables. */
  dependsOn: number[];
  manual: boolean;
}

export interface TaskProposal {
  basis: 'MODULES' | 'USE_CASES';
  existing: { key: string; title: string }[];
  tasks: ProposedTask[];
}

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'À faire',
  IN_PROGRESS: 'En cours',
  DONE: 'Terminée',
  MISSING: 'Introuvable dans Jira',
};

export const TITLE_MAX = 120;
export const OUTCOME_MAX = 300;
