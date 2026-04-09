# Статический анализ (Maven + Jenkins)

## Кратко
В проект добавлены два Maven-профиля:

- `static-analysis-report` — формирует отчеты и **не валит** сборку.
- `static-analysis` — строгий quality gate, **валит** сборку при нарушениях.

## Профили и команды

### 1) Мягкий прогон (для первичного внедрения)
```bash
mvn -B -DskipTests -Pstatic-analysis-report verify
```

Ожидаемое поведение:
- Build status: `SUCCESS`
- Отчеты обновляются в `target/`

### 2) Строгий прогон (для gate)
```bash
mvn -B -DskipTests -Pstatic-analysis verify
```

Ожидаемое поведение:
- Build status: `FAILURE`, если есть нарушения
- Подходит для финального quality gate после очистки текущего техдолга

## Где лежат отчеты

- `target/checkstyle-result.xml`
- `target/pmd.xml`
- `target/spotbugsXml.xml`
- (дополнительно) `target/site/pmd.html`

## Jenkins (Declarative Pipeline) — пример

В репозитории уже добавлен готовый [`Jenkinsfile`](./Jenkinsfile) с таким же подходом.

```groovy
pipeline {
    agent any

    stages {
        stage('Build') {
            steps {
                sh 'mvn -B -DskipTests clean package'
            }
        }

        stage('Static Analysis (Report)') {
            steps {
                sh 'mvn -B -DskipTests -Pstatic-analysis-report verify'
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/checkstyle-result.xml,target/pmd.xml,target/spotbugsXml.xml,target/site/pmd.html', allowEmptyArchive: true
                }
            }
        }

        // Включайте после снижения текущего техдолга
        // stage('Static Analysis (Gate)') {
        //     steps {
        //         sh 'mvn -B -DskipTests -Pstatic-analysis verify'
        //     }
        // }
    }
}
```

## Текущий baseline (на момент внедрения)

- Checkstyle: `787` нарушений
- PMD: `15` нарушений
- SpotBugs: `11` bug instances

## Рекомендованный план внедрения

1. Держать в Jenkins stage `Static Analysis (Report)` как обязательный.
2. Разобрать SpotBugs High/Medium в первую очередь.
3. Ввести целевые пороги по Checkstyle/PMD (или кастомный набор правил).
4. После стабилизации включить `Static Analysis (Gate)`.
