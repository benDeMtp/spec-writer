import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { LifecycleStore } from '../lifecycle/lifecycle.store';
import { ProjectsStore } from '../projects/projects.store';
import { changeLabel } from '../projects/source-changes';
import { PresentationService } from '../interviews/presentation';
import { NotificationsApi } from '../settings/notifications.api';

/** Une source a changé à sa dernière synchronisation (017, contracts/events.md). */
export interface SourceUpdatedEvent {
  id: string;
  projectId: string;
  projectName: string;
  sourceKey: string;
  sourceKind: 'JIRA' | 'DOCUMENTATION' | 'ARCHITECTURE' | null;
  sourceLabel: string;
  added: number;
  modified: number;
  removed: number;
  at: string;
}

/** Verrou partagé par les onglets : seul son détenteur affiche les notifications (FR-017). */
export const NOTIFICATIONS_LOCK = 'spec-writer-notifications';
const RETRY_MS = 10_000;

const KINDS: Record<string, string> = {
  JIRA: 'Jira',
  DOCUMENTATION: 'Documentation',
  ARCHITECTURE: "Modèle d'architecture",
};

/** Titre et corps d'une notification : la source et ses décomptes, jamais de contenu (FR-014). */
export function notificationText(e: SourceUpdatedEvent): { title: string; body: string } {
  const kind = e.sourceKind ? KINDS[e.sourceKind] : 'Source';
  const label = e.sourceKind === 'JIRA' ? e.sourceLabel : `${kind} « ${e.sourceLabel} »`;
  return {
    title: `${label} mise à jour`,
    body: `${e.projectName ? e.projectName + ' : ' : ''}${changeLabel(e)}`,
  };
}

/**
 * Flux des sources mises à jour (017, US3, research.md R5 à R7) : chaque onglet l'écoute, un seul
 * affiche la notification du navigateur, si le réglage est actif et la permission accordée. Un clic
 * mène à la connaissance du projet de la source (FR-018).
 */
@Injectable({ providedIn: 'root' })
export class AppEventsService {
  private readonly api = inject(NotificationsApi);
  private readonly projects = inject(ProjectsStore);
  private readonly router = inject(Router);
  private readonly presentation = inject(PresentationService);
  private readonly lifecycle = inject(LifecycleStore);

  /** Réglage de l'application, relu au démarrage et à chaque enregistrement de la carte. */
  readonly enabled = signal(false);
  private leader = false;
  private source: EventSource | null = null;
  private started = false;

  start(): void {
    if (this.started || typeof EventSource === 'undefined') {
      return;
    }
    this.started = true;
    void this.api.get().then(
      (s) => this.enabled.set(s.sourceUpdates),
      () => undefined,
    );
    this.elect();
    this.connect();
  }

  /** Onglet élu par le verrou du navigateur ; sans Web Locks, chaque onglet affiche (tag dédoublonne). */
  private elect(): void {
    const locks = (navigator as Navigator & { locks?: LockManager }).locks;
    if (!locks) {
      this.leader = true;
      return;
    }
    void locks.request(NOTIFICATIONS_LOCK, () => {
      this.leader = true;
      // gardé jusqu'à la fermeture de l'onglet
      return new Promise<void>(() => undefined);
    });
  }

  private connect(): void {
    const source = new EventSource('/api/events');
    this.source = source;
    source.addEventListener('source-updated', (message) => {
      try {
        void this.show(JSON.parse((message as MessageEvent<string>).data) as SourceUpdatedEvent);
      } catch {
        // événement illisible : ignoré
      }
    });
    source.onerror = () => {
      // 019 : le flux se coupe dès que l'outil s'arrête ; la sonde dit s'il est arrêté
      void this.lifecycle.probe();
      // une session pas encore ouverte ferme le flux : nouvelle tentative plus tard
      if (source.readyState === EventSource.CLOSED) {
        this.source = null;
        setTimeout(() => this.connect(), RETRY_MS);
      }
    };
  }

  /** Le réglage est relu à chaque mise à jour : un autre onglet a pu le changer (FR-016). */
  async show(e: SourceUpdatedEvent): Promise<void> {
    // écran de séance partagé (021) : rien d'étranger à la séance, et rien n'est rejoué après
    if (this.presentation.active()) {
      return;
    }
    if (!this.leader || typeof Notification === 'undefined' || Notification.permission !== 'granted') {
      return;
    }
    try {
      this.enabled.set((await this.api.get()).sourceUpdates);
    } catch {
      return;
    }
    if (!this.enabled()) {
      return;
    }
    const { title, body } = notificationText(e);
    const notification = new Notification(title, { body, tag: e.id });
    notification.onclick = () => {
      window.focus();
      notification.close();
      void this.open(e.projectId);
    };
  }

  private async open(projectId: string): Promise<void> {
    try {
      await this.projects.select(projectId);
    } catch {
      // projet supprimé entre-temps : la connaissance du projet courant reste accessible
    }
    await this.router.navigateByUrl('/projet/connaissance');
  }
}
