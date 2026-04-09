# Статический анализ (Maven + Jenkins)

## Кратко
В проекте используются два профиля Maven:

- `static-analysis-report` — формирует отчеты и не валит сборку.
- `static-analysis` — строгий quality gate, валит сборку при нарушениях.

## Команды

### 1) Мягкий прогон (только отчеты)
```bash
mvn -B -DskipTests -Pstatic-analysis-report verify
```

Ожидаемое поведение:
- статус сборки: `SUCCESS`
- отчеты обновляются в `target/`

### 2) Строгий прогон (quality gate)
```bash
mvn -B -DskipTests -Pstatic-analysis verify
```

Ожидаемое поведение:
- статус сборки: `FAILURE`, если есть нарушения
- используется после снижения текущего техдолга

### 3) Точечная проверка SpotBugs (gate по High/Medium)
```bash
mvn -B -DskipTests com.github.spotbugs:spotbugs-maven-plugin:4.8.6.2:check
```

## Где лежат отчеты

- `target/checkstyle-result.xml`
- `target/pmd.xml`
- `target/spotbugsXml.xml`
- `target/site/checkstyle.html`
- `target/site/pmd.html`

## Jenkins (пример)

В репозитории есть готовый [`Jenkinsfile`](./Jenkinsfile) со стадиями:
- сборка
- soft static analysis (report + archiving artifacts)
- (опционально) строгий gate

## Актуальный baseline (09.04.2026)

Получено командой:
```bash
mvn -B -DskipTests -Pstatic-analysis-report verify
```

- Checkstyle: `778` нарушений
- PMD: `0` нарушений
- SpotBugs report (`spotbugsXml.xml`): `8` low-priority instances
- SpotBugs gate (`spotbugs:check`): `0` (High/Medium отсутствуют)

## Что уже сделано

- Ветка `main` обновлена до коммита `1e6a1b9`
- SpotBugs High/Medium очищен до нуля
- PMD очищен до нуля
- `Jenkinsfile`, профили Maven и документация по запуску добавлены

## Рекомендуемый порядок дальнейшей зачистки

1. Для Checkstyle идти пакетами, а не хаотично:
   - `RegexpSingleline` (хвостовые пробелы)
   - `LineLength`
   - `FinalParameters`
2. После снижения шума включить строгий stage `static-analysis` в Jenkins как обязательный gate.
