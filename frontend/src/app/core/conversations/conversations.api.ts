import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Proposal } from '../jira/jira.model';
import { SessionDetail } from '../sessions/sessions.model';
import {
  ConversationDetail,
  ConversationSummary,
  DiscussionView,
  NeedDraft,
  Started,
  TurnEvent,
} from './conversations.model';

const SILENT = new HttpHeaders({ 'X-Sw-Silent': '1' });

/** Flux d'un tour : fermé par l'écran, ou de lui-même après `done`, `error` ou `gone`. */
export interface TurnStream {
  close(): void;
}

/** Ouvre le flux SSE d'un tour ; remplaçable dans les tests (EventSource n'existe pas hors navigateur). */
export type TurnStreamOpener = (url: string, onEvent: (event: TurnEvent) => void) => TurnStream;

const NAMES = [
  'started',
  'stage',
  'reasoning',
  'step',
  'perimeter',
  'delta',
  'sources',
  'memory',
  'done',
  'error',
  'gone',
] as const;

export const TURN_STREAM = new InjectionToken<TurnStreamOpener>('TURN_STREAM', {
  factory: () => (url, onEvent) => {
    const source = new EventSource(url, { withCredentials: true });
    for (const name of NAMES) {
      source.addEventListener(name, (e) => {
        const data = JSON.parse((e as MessageEvent<string>).data || '{}');
        onEvent({ type: name, ...data } as TurnEvent);
        if (name === 'done' || name === 'error' || name === 'gone') {
          source.close();
        }
      });
    }
    // coupure réseau : l'écran relit la conversation enregistrée
    source.onerror = () => {
      if (source.readyState === EventSource.CLOSED) {
        onEvent({ type: 'gone' });
      }
    };
    return { close: () => source.close() };
  },
});

/** Accès aux conversations (010). Les refus d'envoi sont annoncés par l'écran, pas par le bandeau. */
@Injectable({ providedIn: 'root' })
export class ConversationsApi {
  private readonly http = inject(HttpClient);
  private readonly opener = inject(TURN_STREAM);

  list(projectId: string): Promise<ConversationSummary[]> {
    return firstValueFrom(
      this.http.get<ConversationSummary[]>(
        `/api/projects/${encodeURIComponent(projectId)}/conversations`,
      ),
    );
  }

  /** Exemples de questions tirés de la connaissance du projet ; vide s'il n'a rien d'indexé. */
  suggestions(projectId: string): Promise<string[]> {
    return firstValueFrom(
      this.http.get<string[]>(
        `/api/projects/${encodeURIComponent(projectId)}/conversations/suggestions`,
        {
          headers: SILENT,
        },
      ),
    );
  }

  create(projectId: string): Promise<ConversationSummary> {
    return firstValueFrom(
      this.http.post<ConversationSummary>(
        `/api/projects/${encodeURIComponent(projectId)}/conversations`,
        {},
      ),
    );
  }

  get(id: string): Promise<ConversationDetail> {
    return firstValueFrom(
      this.http.get<ConversationDetail>(`/api/conversations/${encodeURIComponent(id)}`),
    );
  }

  rename(id: string, name: string): Promise<ConversationSummary> {
    return firstValueFrom(
      this.http.patch<ConversationSummary>(
        `/api/conversations/${encodeURIComponent(id)}`,
        { name },
        { headers: SILENT },
      ),
    );
  }

  delete(id: string): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(`/api/conversations/${encodeURIComponent(id)}`, { headers: SILENT }),
    );
  }

  send(id: string, text: string): Promise<Started> {
    return firstValueFrom(
      this.http.post<Started>(
        `/api/conversations/${encodeURIComponent(id)}/messages`,
        { text },
        { headers: SILENT },
      ),
    );
  }

  retry(id: string, userMessageId: string): Promise<Started> {
    return firstValueFrom(
      this.http.post<Started>(
        `/api/conversations/${encodeURIComponent(id)}/messages/${encodeURIComponent(userMessageId)}/retry`,
        {},
        { headers: SILENT },
      ),
    );
  }

  stop(id: string, turnId: string): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(
        `/api/conversations/${encodeURIComponent(id)}/turns/${encodeURIComponent(turnId)}`,
        {
          headers: SILENT,
        },
      ),
    );
  }

  /** Besoin et points proposés par l'agent ; rien n'est enregistré. */
  needDraft(id: string): Promise<NeedDraft> {
    return firstValueFrom(
      this.http.post<NeedDraft>(
        `/api/conversations/${encodeURIComponent(id)}/need-draft`,
        {},
        { headers: SILENT },
      ),
    );
  }

  /** Démarre la spécification avec le brouillon relu ; elle reste liée à la conversation. */
  specify(id: string, draft: NeedDraft): Promise<SessionDetail> {
    return firstValueFrom(
      this.http.post<SessionDetail>(
        `/api/conversations/${encodeURIComponent(id)}/specifications`,
        draft,
        {
          headers: SILENT,
        },
      ),
    );
  }

  /** Conversation dont vient une spécification ; `null` si aucune. */
  origin(sessionId: string): Promise<{ id: string; name: string } | null> {
    return firstValueFrom(
      this.http.get<{ id: string; name: string } | null>(
        `/api/sessions/${encodeURIComponent(sessionId)}/conversation`,
      ),
    );
  }

  follow(id: string, turnId: string, onEvent: (event: TurnEvent) => void): TurnStream {
    return this.opener(
      `/api/conversations/${encodeURIComponent(id)}/turns/${encodeURIComponent(turnId)}/events`,
      onEvent,
    );
  }

  /** « Proposer une nouvelle version » (011) : la rédaction se suit par `follow`. */
  propose(id: string): Promise<{ turnId: string }> {
    return firstValueFrom(
      this.http.post<{ turnId: string }>(
        `/api/conversations/${encodeURIComponent(id)}/proposals`,
        {},
        { headers: SILENT },
      ),
    );
  }

  /** Proposition en attente de relecture ; 404 si elle a expiré ou a été prise. */
  proposal(sessionId: string, proposalId: string): Promise<Proposal> {
    return firstValueFrom(
      this.http.get<Proposal>(
        `/api/sessions/${encodeURIComponent(sessionId)}/proposals/${encodeURIComponent(proposalId)}`,
        { headers: SILENT },
      ),
    );
  }

  /** Abandon : rien n'est créé. */
  discardProposal(sessionId: string, proposalId: string): Promise<void> {
    return firstValueFrom(
      this.http.delete<void>(
        `/api/sessions/${encodeURIComponent(sessionId)}/proposals/${encodeURIComponent(proposalId)}`,
        { headers: SILENT },
      ),
    );
  }

  /** Discussion suivie de la spécification (011) ; `null` s'il n'y en a pas encore. */
  discussion(sessionId: string): Promise<DiscussionView | null> {
    return firstValueFrom(
      this.http.get<DiscussionView | null>(
        `/api/sessions/${encodeURIComponent(sessionId)}/discussion`,
      ),
    );
  }

  /** Ouvre la discussion suivie (créée au besoin), ou en démarre une nouvelle avec `fresh`. */
  openDiscussion(sessionId: string, fresh = false): Promise<DiscussionView> {
    return firstValueFrom(
      this.http.post<DiscussionView>(
        `/api/sessions/${encodeURIComponent(sessionId)}/discussion`,
        { fresh },
        { headers: SILENT },
      ),
    );
  }
}
