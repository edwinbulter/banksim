import { expect, test } from '@playwright/test';
import { OverzichtPagina, RekeningPagina } from '../pages/bank';
import { AUTH, kubectl } from '../support/omgeving';

test.use({ storageState: AUTH.klant });

/**
 * TO §12/§14: valt bank-api weg, dan toont de app een nette melding met "Opnieuw proberen" en werkt hij weer
 * zodra de API terug is. Schaalt de API in het cluster; daarom alleen in Chromium.
 */
test('storing van bank-api: melding en herstel', async ({ page, browserName }) => {
  test.skip(browserName !== 'chromium', 'Schaalt de API in het cluster; één keer per run is genoeg');
  test.setTimeout(240_000);

  const overzicht = new OverzichtPagina(page);
  await overzicht.open();
  const url = await overzicht.betaalrekening().getAttribute('href');

  kubectl('scale', 'deployment/bank-api', '--replicas=0');
  try {
    kubectl('wait', '--for=delete', 'pod', '-l', 'app.kubernetes.io/name=bank-api', '--timeout=90s');
    await page.goto(url!);
    await expect(page.getByRole('alert')).toContainText('De bank is even niet bereikbaar');
    await expect(page.getByRole('button', { name: 'Opnieuw proberen' })).toBeVisible();
  } finally {
    kubectl('scale', 'deployment/bank-api', '--replicas=2');
    kubectl('rollout', 'status', 'deployment/bank-api', '--timeout=180s');
  }

  // De circuit breaker van de BFF staat nog even open; blijven proberen tot het weer lukt.
  const rekening = new RekeningPagina(page);
  await expect(async () => {
    await page.getByRole('button', { name: 'Opnieuw proberen' }).click({ timeout: 2_000 }).catch(() => undefined);
    await expect(rekening.saldo()).toBeVisible({ timeout: 2_000 });
  }).toPass({ timeout: 60_000 });
});
