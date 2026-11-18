# Лабораторная работа №3

- Добавить авторизацию.
- Для авторизации использовать spring-security в комбинации с JWT токеном в хедере Authorization.
- Реализовать ролевую модель.
- Должны быть методы, логика которых меняется в зависимости от текущего пользователя. Пример: Повар не может загружать товары на склад, а логист не может принимать заказы у пользователей.
- Не отдавать пароль в запросе, где участвуют модели пользователя.
- Все пароли должны храниться в хэшированном виде.
- Пользователей могут создавать только супервайзеры.

## Общие условия задания

Использовать стабильные версии фреймворков, библиотек, языков. При написании придерживаться языковым конвенциям [Java](https://www.oracle.com/java/technologies/javase/codeconventions-contents.html), [Kotlin](https://kotlinlang.org/docs/coding-conventions.html).

Следовать принципам разработки ПО, таким к SOLID, DRY, KISS и др.

При разработке использовать git feature branching strategy. При выборе имен для коммитов необходимо придерживаться [conventional commits](https://www.conventionalcommits.org/en/v1.0.0/).

Код должен быть покрыт модульными и интеграционными тестами с помощью testcontainers и junit-jupiter-api, берём лучше [отсюда](https://se.ifmo.ru/courses/testing), ещё можно зайти [сюда](https://docs.google.com/presentation/d/1tcPapWwmrAb0Ju88OILOAiOShcTVbHrvw9ZFii2bLpI/edit?usp=sharing). Минимальный общий процент покрытия кода тестами 70%.

Всё должно запускаться через docker compose.

## Где реализовано

| Что | Где |
| --- | --- |
| `POST /api/v1/auth/login` проверяет email и пароль. Успешный вход выдаёт JWT на 30 минут. | [`AuthController`](user-service/src/main/kotlin/ru/severnoyesiyanieback/user/controller/AuthController.kt), [`AuthService`](user-service/src/main/kotlin/ru/severnoyesiyanieback/user/service/AuthService.kt). |
| Пароли хранятся как хэши PBKDF2. API возвращает только данные профиля и токен при входе. | [`UserService`](user-service/src/main/kotlin/ru/severnoyesiyanieback/user/service/UserService.kt), [`UserResponse`](user-service/src/main/kotlin/ru/severnoyesiyanieback/user/dto/UserResponse.kt), [миграция Flyway](user-service/src/main/resources/db/migration/V3__add_password_hash.sql). |
| Только активный `LORD` создаёт пользователей, меняет их данные и статус и управляет меню. Каждый вошедший пользователь может читать свой профиль. | `SecurityConfig` в [`user-service`](user-service/src/main/kotlin/ru/severnoyesiyanieback/user/security/SecurityConfig.kt) и [`menu-service`](menu-service/src/main/kotlin/ru/severnoyesiyanieback/menu/security/SecurityConfig.kt). |
| Заказ привязан к визиту клиента. `CLIENT` оформляет заказ в свой визит, `WAITER` — в выбранный открытый визит, `WORKER` берёт заказ в работу. | [`OrderController`](visit-order-service/src/main/kotlin/ru/severnoyesiyanieBack/controller/OrderController.kt), [`OrderService`](visit-order-service/src/main/kotlin/ru/severnoyesiyanieBack/service/OrderService.kt). |
| Клиент видит свои визиты, сотрудники видят все. Официант может поменять номер стола и закрыть визит. | [`VisitController`](visit-order-service/src/main/kotlin/ru/severnoyesiyanieBack/controller/VisitController.kt), [`VisitService`](visit-order-service/src/main/kotlin/ru/severnoyesiyanieBack/service/VisitService.kt). |
| Подпись и срок действия JWT проверяет Spring Security. Бизнес-сервисы читают текущую роль и статус аккаунта при защищённых запросах. | [`JwtKeys`](security-support/src/main/kotlin/ru/severnoyesiyanieback/security/JwtKeys.kt), [`SecurityConfig` сервиса визитов и заказов](visit-order-service/src/main/kotlin/ru/severnoyesiyanieBack/security/SecurityConfig.kt). |

Архитектура наследуется из [лабораторной №2](../lab_2/README.md). Публичный API имеет префикс `/api/v1` и доступен через Gateway. Внутренние Feign-запросы используют `/internal/v1` в сети Compose. Эти маршруты не опубликованы через Gateway.

## Роли и доступ

| Роль | Доступ |
| --- | --- |
| Гость | Посмотреть меню и войти. |
| `CLIENT` | Посмотреть свой профиль, открыть свой визит, задать предпочтения и заказать в свой открытый визит. Читать свои визиты и заказы. |
| `WAITER` | Смотреть визиты и заказы, оформлять заказы для открытых визитов клиентов, менять номер столика и закрывать визит. |
| `WORKER` | Смотреть визиты и заказы, взять заказ в работу через `POST /api/v1/orders/{orderId}/start`. |
| `LORD` | Создавать, просматривать и блокировать пользователей, управлять меню, просматривать визиты и заказы. |

При создании заказа JWT определяет, кто отправил запрос. `visitId` указывает, к какому визиту привязать заказ. Если заказ оформляет официант, сервер сохраняет его ID в `waiterId`. Когда бармен берёт заказ, сервер записывает его ID в `workerId`. Второй бармен получает `409`, если заказ уже взят.

## Запуск

Понадобится Docker Desktop. В каталоге `lab_3` создай локальный `.env`. Сгенерируй ключ командой `openssl rand -hex 32` и подставь результат. Для пароля супервайзера используй от 15 до 128 символов.

```dotenv
LAB3_JWT_SECRET='вставь результат openssl'
LAB3_LORD_EMAIL=lord@example.com
LAB3_LORD_PASSWORD='вставь свою парольную фразу'
```

```bash
cd lab_3
docker compose -p lab3 up -d --build
docker compose -p lab3 ps
```

При первом запуске `LordBootstrap` создаёт учётку `LORD` с указанным email. При повторном запуске с тем же email сохраняется текущий пароль. Проверь, что порты `8080` и `8761` свободны, если до этого запускала вторую лабораторную.

Открой [Swagger](http://localhost:8080/swagger-ui/index.html). После запуска дождись регистрации в [Eureka](http://localhost:8761) и обновления списка сервисов у клиентов. Это может занять около минуты. Остановить стенд с сохранением базы можно командой `docker compose -p lab3 down`.

## Проверка в Swagger

При входе выбери **Пользователи → POST /api/v1/auth/login**, передай email и пароль и скопируй `accessToken`. Для вызова защищённой ручки вставь токен в **Authorize** в нужной спецификации. При смене роли замени токен.

1. **Зайди гостем.** В спецификации «Меню» выполни `GET /api/v1/menu`. Ответ `200`. Попробуй `POST /api/v1/users` без токена. Ответ `401`.
2. **Создай три учётки.** Войди под `LORD` и вызови `POST /api/v1/users` с телом ниже. Затем повтори с другими email и ролями `WAITER` и `WORKER`. Ожидается `201`. Ответ содержит `idUser` и роль.
3. **Открой визит и сделай заказ.** Войди как `CLIENT`. В «Посещения и заказы» отправь `POST /api/v1/visits` с `clientId` этой учётки и `tableNumber`. Затем в `POST /api/v1/orders` передай `idVisit` из ответа и позиции из меню. Ожидается `201`.
4. **Дозакажи как официант.** Войди как `WAITER`. `GET /api/v1/visits` покажет визиты. Отправь `POST /api/v1/orders` с тем же `visitId`. Ответ `201` содержит ID вошедшего официанта в `waiterId`. Оба заказа видны в `GET /api/v1/orders`.
5. **Возьми заказ как бармен.** Войди как `WORKER`. Выбери `idOrder` из `GET /api/v1/orders` и вызови `POST /api/v1/orders/{orderId}/start`. Ответ `200` содержит ID бармена в `workerId`.
6. **Проверь запреты.** С токеном `CLIENT` создание пользователя возвращает `403`. Попытка другого клиента заказать в чужой визит тоже возвращает `403`. С токеном `WORKER` создание заказа возвращает `403`, а меню доступно гостю.

Пример создания клиента:

```json
{
  "name": "Анна",
  "email": "anna@example.com",
  "password": "test customer password phrase",
  "role": "CLIENT"
}
```

Пример открытия визита:

```json
{
  "clientId": 2,
  "tableNumber": 5
}
```

Пример создания заказа:

```json
{
  "visitId": 1,
  "items": [
    { "menuItemId": 1, "quantity": 2 }
  ]
}
```

## Автоматические тесты

Из `lab_3` с JDK 21 и запущенным Docker:

```bash
./mvnw clean verify
```

Через Docker Compose:

```bash
docker compose -p lab3-tests -f compose.test.yml run --rm tests
```

Модульные тесты используют JUnit Jupiter, интеграционные поднимают PostgreSQL через Testcontainers. JaCoCo проверяет порог 70% строк и инструкций в каждом бизнес-модуле и в `security-support`.
