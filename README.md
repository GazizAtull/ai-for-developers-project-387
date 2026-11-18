# Календарь звонков (продолжение)

[![hexlet-check](https://github.com/GazizAtull/ai-for-developers-project-387/actions/workflows/hexlet-check.yml/badge.svg)](https://github.com/GazizAtull/ai-for-developers-project-387/actions)

Продолжение учебного проекта Хекслета: та же «Запись на звонок», вокруг которой автоматизируется процесс разработки в GitHub (разбор issue, реализация, ревью, аудит по расписанию).

Начато от версии первого проекта [ai-for-developers-project-386@1026bed](https://github.com/GazizAtull/ai-for-developers-project-386/tree/1026bed). Спецификация, карта решений и тикеты первого проекта: [Issues #1–#17](https://github.com/GazizAtull/ai-for-developers-project-386/issues?q=is%3Aissue).


Упрощённый сервис бронирования времени по мотивам Cal.com. Владелец заводит типы встреч, гость выбирает свободный слот на ближайшие 14 дней и записывается.

Правила и API: [SPEC.md](SPEC.md).

**Демо:** https://call-calendar-4mq9.onrender.com (Render, бесплатный тариф: после 15 минут без запросов сервис засыпает, первый запрос отвечает дольше)

## Сценарий

**Владелец**
1. Открывает вкладку «Владелец».
2. В форме «Новый тип» вводит id (латиница, цифры, дефис), название, описание и длительность (кратно 5 минутам) и жмёт «Создать». Тип появляется в списке.
3. Позже открывает «Предстоящие встречи» и видит записи всех типов: время, тип встречи, имя и email гостя.

**Гость**
1. На главной странице жмёт «Записаться» и выбирает тип встречи.
2. Видит 14 дней, начиная с сегодняшнего. Дни без свободного времени недоступны. Выбирает день и время.
3. Вводит имя и email, жмёт «Записаться» и видит подтверждение.
4. Если слот успел занять кто-то другой, получает сообщение, а список слотов обновляется.

Одно и то же время нельзя занять дважды, даже разными типами встреч.

## Ограничения
- Данные хранятся в памяти процесса: **перезапуск сбрасывает типы встреч и записи**. На Render это происходит и при каждом деплое, и когда бесплатный сервис засыпает.
- Нет авторизации, уведомлений и внешних календарей. Владелец один.

## Запуск

Нужен [mise](https://mise.jdx.dev). Он ставит Java 21 и Node 22.

```bash
mise run install
mise run dev-backend    # http://localhost:8080
mise run dev-frontend   # http://localhost:5173, /api проксируется на :8080
```

В Docker:

```bash
mise run build
docker run --rm -e PORT=8080 -p 8080:8080 call-calendar   # http://localhost:8080
```

Приложение слушает порт из переменной `PORT` (по умолчанию 8080).

## Деплой на Render

Сервис описан в [`render.yaml`](render.yaml): Docker-образ из `Dockerfile`, бесплатный тариф, Render сам передаёт `PORT`, health check `/api/event-types`.

### MCP Render для агента

Конфигурация в [`.mcp.json`](.mcp.json) (Claude Code), ключ в репозиторий не попадает:

1. В Render: Account Settings → API Keys → Create API Key.
2. Экспортируйте ключ в окружение перед запуском агента:
   ```bash
   export RENDER_API_KEY=rnd_...
   ```
3. Запустите `claude` в корне репозитория и подтвердите MCP-сервер `render`. Агент может выбрать workspace, создать web service из репозитория, смотреть деплои и логи.

Не коммитьте файлы с ключами: `.env` и `.env.*` в `.gitignore`.

```bash
# пример ручного запуска образа как на Render
docker run --rm -e PORT=10000 -p 10000:10000 call-calendar
```

## Агенты в GitHub

| Workflow | Событие | Модель | Назначение | Где результат |
|---|---|---|---|---|
| [`opencode.yml`](.github/workflows/opencode.yml) | `issue_comment`, `pull_request_review_comment` (`types: [created]`) с командой `/oc` или `/opencode` | `opencode/big-pickle` (бесплатная модель OpenCode, ключ `OPENCODE_API_KEY` в Secrets) | разбор issue, спецификация и тикеты (`to-spec`, `to-tickets`), реализация (`implement-spec`), правки PR по ревью | ответ комментарием от OpenCode App в issue/PR, прогон во вкладке Actions → opencode |
| [`triage.yml`](.github/workflows/triage.yml) | `issues` (`types: [opened]`) | `opencode/big-pickle` | автотриаж входящего обращения по скиллу `triage`: что уже умеет приложение, вопросы, метки `needs-triage`/`needs-info`, следующий шаг. Код не меняет | комментарий OpenCode App в новом issue, прогон в Actions → triage |

| [`review.yml`](.github/workflows/review.yml) | `pull_request` (`opened`, `synchronize`, `reopened`, `ready_for_review`), только ветки этого репозитория | `opencode/big-pickle` через OpenCode CLI | независимое авторевью по [`REVIEW.md`](REVIEW.md): сверка со спецификацией и согласованием по ссылкам из PR, diff, статусы CI. Код не меняет | комментарий «Авторевью» в PR с проверенным SHA, прогон в Actions → review |
| [`ci.yml`](.github/workflows/ci.yml) | `push`, `pull_request` | — | `mise run check` на head SHA PR, браузерный сценарий с trace | артефакт `e2e-<sha>` (Playwright report/trace + `checked-sha.txt`) в прогоне ci |

- **Авторевью** работает отдельно от реализации: обычный OpenCode CLI готовит текст, шаг `gh pr comment` публикует его с `GITHUB_TOKEN` (`contents`, `issues`, `actions`, `checks`, `statuses`: read, `pull-requests: write`). Права агента в ревью сужены `.github/opencode-review.json` (без правок файлов). `timeout-minutes: 20`, `concurrency` отменяет устаревшее ревью того же PR. PR агента тоже получают ревью; fork не получает секрет. Исправления запускает только человек командой `/oc`.
- **Бюджет агента на PR:** не больше 3 допущенных `/oc` (общих и построчных, включая Re-run). Задание `budget` считает попытки по истории Actions до вызова модели; при исчерпании задание `report` без модели пишет в PR причину `limit_exhausted`, SHA и ссылку на логи. Другие причины: `timeout`, `infra` (нет ключа/сбой подготовки), `agent_failed`.
- **Автотриаж** работает по `prompt` в воркфлоу, без команды. Запускается только для обращений доверенных участников (`OWNER`, `MEMBER`, `COLLABORATOR`), не для ботов. Тикеты от `to-spec`/`to-tickets` (метки `ready-for-agent`, `ready-for-human`, см. `docs/agents/triage-labels.md`) и созданные ботом пропускаются. `timeout-minutes: 15`, один прогон на issue.
- **`opencode.json`:** `permission.external_directory: deny`. На раннере некому отвечать на запросы разрешений, поэтому доступ за пределы репозитория запрещён сразу, без вопроса (иначе прогон висит до тайм-аута).
- **Агент:** OpenCode GitHub App, установлен на репозиторий. Авторизация стандартная: OIDC-токен обменивается на токен приложения, поэтому у workflow только `contents: read` и `id-token: write`, у checkout `persist-credentials: false`.
- **Команды (`mentions`):** набор по умолчанию, `/opencode` и `/oc`.
- **`share`:** решение: сессии агента не публикуются (репозиторий открытый). В `anomalyco/opencode/github@v1.19.6` параметр `share` устарел и игнорируется, публикация сессий отключена самим OpenCode, поэтому в воркфлоу его нет. Версия action зафиксирована: `@latest` падал на получении версии.
- **Ограничения:** запускают только `OWNER`, `MEMBER`, `COLLABORATOR`; комментарии ботов игнорируются; fork-PR секреты не получают (`pull_request_target` не используется); `timeout-minutes: 30`; один прогон на issue/PR (`concurrency`). Остановить: Actions → opencode → Cancel workflow, отключить совсем: Disable workflow.
- **Скиллы на раннере:** проектные скиллы лежат в `.agents/skills/` и приезжают с checkout, настройки трекера в `docs/agents/`.

## Проверки

```bash
mise run check
```

Проверяет, что `openapi.yaml` и SDK соответствуют контракту, затем линтер, типы, юнит- и API-тесты, браузерный сценарий Playwright. Эту же команду запускает GitHub Actions на каждый push.

## API-контракт
Источник правды: `contract/main.tsp` (TypeSpec). После правки: `mise run generate` обновит `openapi.yaml` и клиентский SDK `frontend/src/api`.

## Соглашения
Коммиты по [Conventional Commits](https://www.conventionalcommits.org). Рабочие правила для агентов: [AGENTS.md](AGENTS.md).
