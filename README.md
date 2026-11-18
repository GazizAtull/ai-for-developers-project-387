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

## Проверки

```bash
mise run check
```

Проверяет, что `openapi.yaml` и SDK соответствуют контракту, затем линтер, типы, юнит- и API-тесты, браузерный сценарий Playwright. Эту же команду запускает GitHub Actions на каждый push.

## API-контракт
Источник правды: `contract/main.tsp` (TypeSpec). После правки: `mise run generate` обновит `openapi.yaml` и клиентский SDK `frontend/src/api`.

## Соглашения
Коммиты по [Conventional Commits](https://www.conventionalcommits.org). Рабочие правила для агентов: [AGENTS.md](AGENTS.md).
