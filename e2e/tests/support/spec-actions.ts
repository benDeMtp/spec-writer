import type { Locator, Page } from '@playwright/test';

/**
 * Action secondaire de la page d'une spécification, dans le menu « Autres actions » : le menu est
 * ouvert au besoin, l'entrée est rendue pour être cliquée ou vérifiée.
 */
export async function specAction(page: Page, name: string): Promise<Locator> {
  const menu = page.getByRole('menu');
  if (!(await menu.isVisible())) {
    await page.getByRole('button', { name: /^Autres actions/ }).click();
  }
  return menu.getByRole('menuitem', { name });
}

/** Ferme le menu « Autres actions » s'il est ouvert. */
export async function closeActions(page: Page): Promise<void> {
  if (await page.getByRole('menu').isVisible()) {
    await page.keyboard.press('Escape');
  }
}

/** Sélecteur de version de la page d'une spécification : ouvre le menu des versions. */
export async function versionMenu(page: Page): Promise<Locator> {
  await page.getByRole('button', { name: /^Version \d+, changer de version$/ }).click();
  return page.getByRole('menu');
}
