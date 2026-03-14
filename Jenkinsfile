pipeline {
    agent any

    tools {
        jdk 'JDK21'
        maven 'Maven3'
    }

    environment {
        SPRING_PROFILES_ACTIVE = 'ci'
    }

    options {
        gitLabConnection('gitlab')
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                gitlabCommitStatus('build') {
                    sh 'mvn clean compile'
                }
            }
        }

        stage('Unit Tests') {
            steps {
                gitlabCommitStatus('test') {
                    sh 'mvn test'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                gitlabCommitStatus('sonar') {
                    withSonarQubeEnv('sonar') {
                        sh '''
                        mvn sonar:sonar \
                        -Dsonar.projectKey=essl-attendance \
                        -Dsonar.projectName="Attendance Report"
                        '''
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }
    }
}
