# API Set10 Payment Checklist Report (2026-04-09)

Проект: `F:\Projects\set10-payment-plugin`  
Плагин: `uz.sbgpay.payment`

## Сводка

- `PASS`: 6
- `PARTIAL`: 4
- `N/A`: 4
- `BLOCKED`: 9
- `FAIL`: 0

## Что проверено

1. Юнит-тесты: `mvn -B -DskipTests=false test` (30/30, BUILD SUCCESS, 09.04.2026 20:09 +05:00)
2. Статанализ: `mvn -B -DskipTests -Pstatic-analysis verify` (Checkstyle/PMD/SpotBugs: 0 нарушений, BUILD SUCCESS, 09.04.2026 20:10 +05:00)
3. Валидация `metainf.xml`: `java -jar D:\Set10sdk\utils\MetainfValidator.jar src/main/resources/metainf.xml` (`Validation success.`)
4. Проверка `MANIFEST.MF` в `target/SBGPayPaymentPlugin-2.0.0.jar` и `target/SBGPayPaymentPlugin-2.0.0-jar-with-dependencies.jar`
5. Добавлена локализация `uz` (`src/main/resources/strings_uz.xml`), набор ключей синхронизирован с `strings_ru.xml`/`strings_en.xml` (без пропусков)

## Статусы по чеклисту (1–23)

Легенда:
- `PASS` — подтверждено
- `PARTIAL` — подтверждена только часть результата (обычно API-логика, но не кассовый/серверный контур)
- `N/A` — не применимо к текущей реализации/ограничению API
- `BLOCKED` — нужна проверка на стенде Set10 (сервер/касса/ФР/ОФД/ERP)

| # | Статус | Комментарий |
|---|---|---|
| 1 | PASS | `MANIFEST.MF` приведен к требованиям tutorial: есть `Build-Date`, `Implementation-Version`, `Project`, `Build-Machine`, `Branch`, `Implementation-Vendor`, `Revision`, `Vendor-Email`. |
| 2 | PASS | `metainf.xml` валиден (`MetainfValidator: Validation success`). |
| 3 | PASS | Локализация содержит и тип оплаты, и процессинг (`payment.name`, `service.name`) для `ru/en/uz`. |
| 4 | N/A | В проекте целевой build-tool — Maven; чеклистовый пункт про Gradle не применим к данному пайплайну при наличии успешной Maven-сборки артефакта. |
| 5 | BLOCKED | Требуется установка jar на сервер Set10 и проверка UI/типов оплат. |
| 6 | BLOCKED | Требуется проверка соответствия экранов серверных настроек `ExternalService/Options`. |
| 7 | N/A | В `PaymentPlugin` не используется отдельный блок `<Options>` (только параметры внешнего сервиса + стандартные флаги типа оплаты Set10). |
| 8 | BLOCKED | Нужна проверка сохранения настроек в БД сервера Set10. |
| 9 | BLOCKED | Нужна проверка доставки настроек на кассу после сохранения на сервере. |
| 10 | BLOCKED | Нужна установка jar на кассу и проверка доступности типа оплаты после рестарта. |
| 11 | BLOCKED | Нужна визуальная проверка длины отображаемого названия оплаты на кассе. |
| 12 | PARTIAL | API-сценарий успешной оплаты подтвержден логами (`paid/completed`), но ФР/БД/ОФД — только стенд. |
| 13 | PARTIAL | Подтверждён API-сценарий аннулирования частично оплаченного чека: при `status=completed` выполняется `POST /reversal` + polling до `refunded` (лог 09.04.2026 15:22:35–15:22:39). Печать ФР/ОФД/серверная регистрация требуют отдельного стендового подтверждения. |
| 14 | PARTIAL | Рефанд через `POST /payments/{id}/reversal` + polling до `refunded` подтвержден логами, но печать/ОФД/БД — стенд. |
| 15 | N/A | Частичный возврат намеренно запрещен: API reversal работает от исходного `paymentId` и полной суммы. |
| 16 | N/A | Произвольный возврат без исходного `paymentId` не поддерживается контрактом `POST /payments/{id}/reversal`. |
| 17 | PASS | Подтверждено на стенде отключением сети кассы: `UnknownHostException: sbg.amasia.io`, в UI показано сообщение `Ошибка загрузки способов оплаты...`, неподтверждённый платёж не принят (лог 09.04.2026 15:27:57–15:28:38). |
| 18 | BLOCKED | Сценарий «медленный ответ» отдельно не воспроизведён; нужен прокси/стаб с искусственной задержкой `/status` дольше `pollTimeoutSeconds` для проверки timeout-ветки. |
| 19 | PARTIAL | В коде данные пишутся в `payment.getData()`, `PersistedField` объявлены; факт записи в БД кассы — только стенд. |
| 20 | BLOCKED | Нужна проверка в “Операционном дне” сервера, что отображаются `visible=true` поля. |
| 21 | BLOCKED | Нужна фактическая выгрузка в ERP и сверка `exportable=true` полей. |
| 22 | PASS | Причины недоступности оплаты логируются на `INFO` (`isAvailable=false: ... not configured`). |
| 23 | PASS | Описание бизнес-сценариев присутствует в документации проекта (`README.md`). |

## Подтвержденные артефакты

- `MANIFEST.MF` содержит:
  - `Build-Date: 09.04.2026 09:30:35`
  - `Implementation-Version: 2.0.0`
  - `Project: SBG Pay Payment Plugin`
  - `Build-Machine: SET`
  - `Branch: main`
  - `Revision: f1d4a52`
  - `Implementation-Vendor: SBG (Soft Business Group)`
  - `Vendor-Email: support@sbgpay.uz`
- `metainf.xml` содержит необходимые `Options` и `PersistedField` для оплаты/возврата.
- `README.md` обновлён под фактическую локализацию `ru/en/uz`.

## Дополнительно

Подробный стендовый план закрытия `BLOCKED` пунктов: [SET10_STAND_E2E_TEST_PLAN_2026-04-09.md](/F:/Projects/set10-payment-plugin/SET10_STAND_E2E_TEST_PLAN_2026-04-09.md)
