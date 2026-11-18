import { expect, test, type Page } from '@playwright/test';

async function createTypeAsOwner(page: Page, id: string, title: string, minutes: number) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Владелец' }).click();
  await page.getByLabel('Id (латиница, цифры, дефис)').fill(id);
  await page.getByLabel('Название').fill(title);
  await page.getByLabel('Длительность, мин (кратно 5)').fill(String(minutes));
  await page.getByRole('button', { name: 'Создать' }).click();
  await expect(page.locator('.list').first()).toContainText(title);
}

async function openFirstFreeSlot(page: Page, title: string) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Записаться' }).click();
  await page.getByRole('button', { name: new RegExp(title) }).click();
  await expect(page.getByRole('heading', { name: 'Выберите день и время' })).toBeVisible();
  const slot = page.locator('.slots button').first();
  const time = await slot.textContent();
  await slot.click();
  return time;
}

async function fillGuest(page: Page, name: string) {
  await page.getByLabel('Имя').fill(name);
  await page.getByLabel('Email').fill(`${name.toLowerCase()}@example.com`);
  await page.locator('form').getByRole('button', { name: 'Записаться' }).click();
}

test('гость записывается на свободный слот, владелец видит встречу', async ({ page }) => {
  const suffix = Date.now().toString(36);
  const title = `Знакомство ${suffix}`;
  await createTypeAsOwner(page, `intro-${suffix}`, title, 30);

  await openFirstFreeSlot(page, title);
  await fillGuest(page, 'Anya');
  await expect(page.getByRole('heading', { name: 'Вы записаны' })).toBeVisible();

  await page.getByRole('button', { name: 'Владелец' }).click();
  await expect(page.getByText('anya@example.com')).toBeVisible();
  await expect(page.getByText(title).last()).toBeVisible();
});

test('занятое время: гость видит сообщение о конфликте', async ({ browser }) => {
  const suffix = Date.now().toString(36);
  const title = `Консультация ${suffix}`;
  const owner = await browser.newPage();
  await createTypeAsOwner(owner, `consult-${suffix}`, title, 60);

  // два гостя открыли один и тот же слот, первый успевает записаться
  const first = await browser.newPage();
  const second = await browser.newPage();
  const t1 = await openFirstFreeSlot(first, title);
  const t2 = await openFirstFreeSlot(second, title);
  expect(t2).toBe(t1);

  await fillGuest(first, 'Boris');
  await expect(first.getByRole('heading', { name: 'Вы записаны' })).toBeVisible();

  await fillGuest(second, 'Vika');
  await expect(second.locator('.error')).toHaveText('Это время уже занято');
  await expect(second.getByRole('heading', { name: 'Вы записаны' })).toHaveCount(0);
});
