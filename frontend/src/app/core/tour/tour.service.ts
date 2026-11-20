import { LiveAnnouncer } from '@angular/cdk/a11y';
import { Overlay, OverlayRef } from '@angular/cdk/overlay';
import { ComponentPortal } from '@angular/cdk/portal';
import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { SessionsApi } from '../sessions/sessions.api';
import { SPECIFICATION, TOUR_STOPS, TourStop } from './tour-stops';
import { TourBubbleComponent } from './tour-bubble.component';

/** Délai d'attente de la cible d'un arrêt, le temps que l'écran se charge. */
const TARGET_WAIT_MS = 2000;
const HIGHLIGHT = 'sw-tour-target';

/**
 * Visite guidée (018, US2, research.md R6) : ouvre l'écran de chaque arrêt, met en valeur sa cible
 * et y accroche une bulle. Elle ne fait que des lectures (FR-017) ; la quitter ramène à l'écran de
 * départ (FR-016).
 */
@Injectable({ providedIn: 'root' })
export class TourService {
  private readonly overlay = inject(Overlay);
  private readonly router = inject(Router);
  private readonly announcer = inject(LiveAnnouncer);
  private readonly sessions = inject(SessionsApi);
  private readonly document = inject(DOCUMENT);

  readonly stops: TourStop[] = TOUR_STOPS;
  /** Arrêt affiché ; `null` hors de la visite. */
  readonly current = signal<number | null>(null);
  private origin = '/';
  private ref: OverlayRef | null = null;
  private highlighted: HTMLElement | null = null;
  private specification: string | null | undefined;

  async start(): Promise<void> {
    if (this.current() !== null) {
      return;
    }
    this.origin = this.router.url;
    this.specification = undefined;
    await this.show(0);
  }

  async next(): Promise<void> {
    const i = this.current();
    if (i === null) {
      return;
    }
    if (i + 1 >= this.stops.length) {
      await this.quit();
      return;
    }
    await this.show(i + 1);
  }

  async previous(): Promise<void> {
    const i = this.current();
    if (i !== null && i > 0) {
      await this.show(i - 1);
    }
  }

  /** Ferme la visite et ramène à l'écran d'où elle a été lancée. */
  async quit(): Promise<void> {
    if (this.current() === null) {
      return;
    }
    this.close();
    this.current.set(null);
    await this.router.navigateByUrl(this.origin);
  }

  private async show(index: number): Promise<void> {
    const stop = this.stops[index];
    const url = await this.url(stop);
    if (this.router.url !== url) {
      const moved = await this.router.navigateByUrl(url);
      if (!moved) {
        // une saisie non enregistrée a retenu la page : la visite reste sur l'arrêt courant
        void this.announcer.announce('La visite reste sur cet écran : une saisie n’est pas enregistrée.', 'assertive');
        return;
      }
    }
    this.close();
    this.current.set(index);
    const target = await this.waitFor(stop.target);
    this.attach(stop, index, target);
    void this.announcer.announce(`Arrêt ${index + 1} sur ${this.stops.length} : ${stop.title}`, 'polite');
  }

  /** Spécification la plus récente du projet courant, par lecture seule ; sinon la liste. */
  private async url(stop: TourStop): Promise<string> {
    if (stop.route !== SPECIFICATION) {
      return stop.route;
    }
    if (this.specification === undefined) {
      try {
        const list = await this.sessions.list();
        const latest = list.filter((s) => s.readable && s.latestVersion !== null).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))[0];
        this.specification = latest ? `/sessions/${latest.id}` : null;
      } catch {
        this.specification = null;
      }
    }
    return this.specification ?? '/sessions';
  }

  private async waitFor(target: string): Promise<HTMLElement | null> {
    const selector = `[data-tour="${target}"]`;
    const deadline = Date.now() + TARGET_WAIT_MS;
    let found = this.document.querySelector<HTMLElement>(selector);
    while (!found && Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, 50));
      found = this.document.querySelector<HTMLElement>(selector);
    }
    return found;
  }

  private attach(stop: TourStop, index: number, target: HTMLElement | null): void {
    const position = target
      ? this.overlay
          .position()
          .flexibleConnectedTo(target)
          .withPush(true)
          .withViewportMargin(16)
          .withPositions([
            { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 12 },
            { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -12 },
            { originX: 'end', originY: 'center', overlayX: 'start', overlayY: 'center', offsetX: 12 },
          ])
      : this.overlay.position().global().centerHorizontally().centerVertically();
    this.ref = this.overlay.create({
      positionStrategy: position,
      scrollStrategy: this.overlay.scrollStrategies.reposition(),
      hasBackdrop: true,
      backdropClass: 'cdk-overlay-transparent-backdrop',
    });
    this.ref.keydownEvents().subscribe((event) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        void this.quit();
      }
    });
    if (target) {
      target.scrollIntoView?.({ block: 'center' });
      target.classList.add(HIGHLIGHT);
      this.highlighted = target;
    }
    const bubble = this.ref.attach(new ComponentPortal(TourBubbleComponent));
    bubble.setInput('index', index);
    bubble.setInput('total', this.stops.length);
    bubble.setInput('title', stop.title);
    bubble.setInput('text', target ? stop.text : stop.fallback);
    bubble.instance.previous.subscribe(() => void this.previous());
    bubble.instance.next.subscribe(() => void this.next());
    bubble.instance.quit.subscribe(() => void this.quit());
    this.ref.updatePosition();
  }

  private close(): void {
    this.highlighted?.classList.remove(HIGHLIGHT);
    this.highlighted = null;
    this.ref?.dispose();
    this.ref = null;
  }
}
