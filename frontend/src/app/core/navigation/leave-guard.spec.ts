import { TestBed } from '@angular/core/testing';
import { LeaveGuard } from './leave-guard';

describe('LeaveGuard', () => {
  let guard: LeaveGuard;

  beforeEach(() => {
    guard = TestBed.inject(LeaveGuard);
  });

  it('laisse partir quand aucune page ne retient', async () => {
    expect(await guard.confirm()).toBe(true);
  });

  it('suit la réponse de la page enregistrée', async () => {
    guard.register(() => false);
    expect(await guard.confirm()).toBe(false);
    guard.register(() => Promise.resolve(true));
    expect(await guard.confirm()).toBe(true);
  });

  it('oublie la vérification retirée', async () => {
    const release = guard.register(() => false);
    release();
    expect(await guard.confirm()).toBe(true);
  });

  it('ne retire pas la vérification d’une autre page', async () => {
    const release = guard.register(() => true);
    guard.register(() => false);
    release();
    expect(await guard.confirm()).toBe(false);
  });
});
