# AGENTS.md

Сервис записи на звонок (упрощённый Cal.com). Спецификация: `SPEC.md`, сценарий и запуск: `README.md`.

## Команды (нужен mise)
- `mise run install` зависимости
- `mise run check` все проверки: актуальность сгенерированного, линтер, типы, юнит-, API- и браузерные тесты (Playwright поднимает настоящий бэкенд). Её же запускает CI
- `mise run check:e2e` только браузерный сценарий (`frontend/e2e/`)
- `mise run generate` пересобрать `openapi.yaml` и SDK после правки контракта
- `mise run dev-backend` и `mise run dev-frontend` запуск локально (:8080 и :5173)
- `mise run build` Docker-образ

## Устройство
- `contract/main.tsp` источник правды для API. Из него генерируются `openapi.yaml` и `frontend/src/api`
- `backend/` Java 21, Spring Boot. Вся логика в `app.calendar.domain.CalendarService` (без Spring); `web/` только HTTP
- `frontend/` React, Vite, TypeScript. Запросы только через `src/api.ts` поверх сгенерированного SDK

## Правила
- Генерируемые пути: `openapi.yaml` и `frontend/src/api/`. Руками не править: менять `contract/main.tsp` и запускать `mise run generate`. CI повторяет генерацию и падает на любых изменениях или новых файлах (`mise run check:generated`)
- Фронтенд ходит в API только через сгенерированный SDK (`src/api.ts`), без ручного `fetch`
- Правила расписания (часы, шаг, окно, пояс) живут в `ScheduleConfig` и в `SPEC.md`; менять в обоих местах
- Проверка пересечений и запись брони только под замком в `CalendarService`
- Коммиты по Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`, `chore:`
- Релизы: release-please (`.github/workflows/release-please.yml`) собирает release-PR и `CHANGELOG.md` из коммитов `feat:`/`fix:` в `main`. Версию руками не менять
- Коммит реализации ссылается на тикет, который закрывает: `feat: запись на свободный слот (#12)`

## Agent skills

Набор mattpocock/skills лежит в `.agents/skills/`, версии в `skills-lock.json`. Восстановить и подключить к агенту: `npx skills experimental_install`.

### Issue tracker

Задачи, спецификации и тикеты живут в GitHub Issues репозитория GazizAtull/ai-for-developers-project-387. История первого проекта (карта, спецификация, тикеты) осталась в [GazizAtull/ai-for-developers-project-386](https://github.com/GazizAtull/ai-for-developers-project-386/issues): см. раздел «Исходные решения» в `docs/agents/issue-tracker.md`. См. `docs/agents/issue-tracker.md`.

### Triage labels

Метки по умолчанию: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. См. `docs/agents/triage-labels.md`.

### Domain docs

Один контекст: словарь в `GLOSSARY.md`, решения в `docs/adr/`. См. `docs/agents/domain.md`.
