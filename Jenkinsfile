pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    parameters {
        booleanParam(
            name: 'STATIC_ANALYSIS_GATE',
            defaultValue: false,
            description: 'Если включено, запускается строгий quality gate (-Pstatic-analysis).'
        )
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'mvn -B -DskipTests clean package'
                    } else {
                        bat 'mvn -B -DskipTests clean package'
                    }
                }
            }
        }

        stage('Static Analysis Report') {
            steps {
                script {
                    if (isUnix()) {
                        sh 'mvn -B -DskipTests -Pstatic-analysis-report verify'
                    } else {
                        bat 'mvn -B -DskipTests -Pstatic-analysis-report verify'
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'target/checkstyle-result.xml,target/pmd.xml,target/spotbugsXml.xml,target/site/pmd.html', allowEmptyArchive: true
                }
            }
        }

        stage('Static Analysis Gate') {
            when {
                expression { return params.STATIC_ANALYSIS_GATE }
            }
            steps {
                script {
                    if (isUnix()) {
                        sh 'mvn -B -DskipTests -Pstatic-analysis verify'
                    } else {
                        bat 'mvn -B -DskipTests -Pstatic-analysis verify'
                    }
                }
            }
        }
    }
}

