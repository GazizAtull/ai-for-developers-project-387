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

async function fillGuest(page: Page, name: string, topic?: string) {
  await page.getByLabel('Имя').fill(name);
  await page.getByLabel('Email').fill(`${name.toLowerCase()}@example.com`);
  if (topic !== undefined) {
    await page.getByLabel('Тема разговора').fill(topic);
  }
  await page.locator('form').getByRole('button', { name: 'Записаться' }).click();
}

test('гость записывается с темой: тема видна гостю и владельцу', async ({ page }) => {
  const suffix = Date.now().toString(36);
  const title = `Знакомство ${suffix}`;
  const guest = `anya-${suffix}`;
  const topic = `Обсудить проект ${suffix}\nи сроки`;
  await createTypeAsOwner(page, `intro-${suffix}`, title, 30);

  await openFirstFreeSlot(page, title);
  await fillGuest(page, guest, topic);
  await expect(page.getByRole('heading', { name: 'Вы записаны' })).toBeVisible();
  await expect(page.locator('.topic')).toContainText(`Обсудить проект ${suffix}`);

  await page.getByRole('button', { name: 'Владелец' }).click();
  const row = page.locator('li', { hasText: `${guest}@example.com` });
  await expect(row).toBeVisible();
  await expect(row.locator('.topic')).toContainText(`Обсудить проект ${suffix}`);
});

test('бронь без темы работает как раньше: строки темы нет', async ({ page }) => {
  const suffix = Date.now().toString(36);
  const title = `Знакомство ${suffix}`;
  const guest = `boris-${suffix}`;
  await createTypeAsOwner(page, `intro-${suffix}`, title, 30);

  await openFirstFreeSlot(page, title);
  await fillGuest(page, guest);
  await expect(page.getByRole('heading', { name: 'Вы записаны' })).toBeVisible();
  await expect(page.locator('.topic')).toHaveCount(0);

  await page.getByRole('button', { name: 'Владелец' }).click();
  const row = page.locator('li', { hasText: `${guest}@example.com` });
  await expect(row).toBeVisible();
  await expect(row.locator('.topic')).toHaveCount(0);
});

test('занятое время с темой: гость видит сообщение о конфликте', async ({ browser }) => {
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

  await fillGuest(first, `boris-${suffix}`, 'Тема первого гостя');
  await expect(first.getByRole('heading', { name: 'Вы записаны' })).toBeVisible();
  await expect(first.locator('.topic')).toContainText('Тема первого гостя');

  await fillGuest(second, `vika-${suffix}`, 'Тема второго гостя');
  await expect(second.locator('.error')).toHaveText('Это время уже занято');
  await expect(second.getByRole('heading', { name: 'Вы записаны' })).toHaveCount(0);
});

test('тема длиннее 300 символов отклоняется сервером', async ({ page, request }) => {
  const suffix = Date.now().toString(36);
  const id = `topic-${suffix}`;
  const title = `Тема ${suffix}`;
  await createTypeAsOwner(page, id, title, 30);

  const availability = await (
    await request.get(`/api/event-types/${id}/availability`)
  ).json();
  const slot = availability.days.find((d: { slots: unknown[] }) => d.slots.length > 0).slots[0];
  const payload = {
    eventTypeId: id,
    start: slot.start,
    guestName: 'Anya',
    guestEmail: `anya-${suffix}@example.com`,
  };

  const tooLong = await request.post('/api/bookings', {
    data: { ...payload, topic: 'я'.repeat(301) },
  });
  expect(tooLong.status()).toBe(400);
  expect((await tooLong.json()).code).toBe('VALIDATION_ERROR');

  const max = await request.post('/api/bookings', {
    data: { ...payload, topic: 'я'.repeat(300) },
  });
  expect(max.status()).toBe(201);
});
