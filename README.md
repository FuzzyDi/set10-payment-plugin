# SBG Pay Plugin v2 для Set Retail 10

Платёжный плагин для интеграции Set Retail 10 с SBG Pay POS API v1.

## Возможности

- QR-оплата через Click, Payme, Uzcard и другие провайдеры
- Отображение QR-кода на дисплее покупателя (CustomerDisplay)
- Автоматический опрос статуса платежа
- Передача состава чека (опционально)
- Поддержка локализации (ru/en)

## Требования

- Set Retail 10 с API версии 1.20.0+
- Java 8+
- Device Token от SBG Pay

## Установка

1. Соберите плагин:
```bash
mvn clean package
```

2. Скопируйте JAR файл в папку плагинов Set Retail 10:
```
plugins/sbgpay-payment-plugin-2.0.0.jar
```

3. Перезапустите кассу

## Настройка

В настройках внешней системы "SBG Pay (QR-оплата)" укажите:

| Параметр | Описание | По умолчанию |
|----------|----------|--------------|
| `sbgpay.baseUrl` | URL сервера POS API | `https://sbg.amasia.io/pos` |
| `sbgpay.deviceToken` | Токен устройства (обязательно) | - |
| `sbgpay.lang` | Язык (ru/uz/en) | `ru` |
| `sbgpay.currency` | Валюта | `UZS` |
| `sbgpay.ttlSeconds` | Время жизни платежа (сек) | `300` |
| `sbgpay.pollDelayMs` | Интервал опроса статуса (мс) | `2000` |
| `sbgpay.pollTimeoutSeconds` | Таймаут ожидания оплаты (сек) | `420` |
| `sbgpay.sendReceipt` | Отправлять состав чека | `false` |

## Процесс оплаты

```
1. Кассир выбирает "Оплата через SBG Pay"
2. GET /api/v1/payment-methods → список методов оплаты
3. Кассир выбирает метод (Click, Payme, etc.)
4. POST /api/v1/payments → создание платежа
5. GET /api/v1/payments/{id}/status → получение qrPayload
6. QR-код отображается на дисплее покупателя
7. Polling статуса до completed/failed/expired
8. Платёж завершается
```

## Сохраняемые данные платежа

В чек записываются:
- `sbgpay.paymentId` — UUID платежа
- `sbgpay.paymentCode` — Код платежа (pay_xxxxx)
- `sbgpay.methodId` — ID метода оплаты
- `sbgpay.methodName` — Название метода (Click, Payme, etc.)
- `sbgpay.status` — Финальный статус

## Формат receipt.items

При `sbgpay.sendReceipt=true`:

```json
{
  "receipt": {
    "items": [
      {
        "lineId": "1",
        "name": "Молоко 3.2%",
        "sku": "12345",
        "barcode": "4607001234567",
        "price": 5000,
        "qty": 2.0,
        "total": 10000
      }
    ]
  }
}
```

## Структура проекта

```
SBGPayPluginV2/
├── pom.xml
├── lib/
│   └── set10pos-api-1.20.0.jar
└── src/main/
    ├── java/uz/sbgpay/set10/payment/
    │   └── SbgPayPaymentPlugin.java
    └── resources/
        ├── metainf.xml
        ├── strings_ru.xml
        └── strings_en.xml
```

## Лицензия

Proprietary © SBG Pay

## Контакты

- API документация: https://sbg.amasia.io/docs
- Поддержка: support@sbgpay.uz
