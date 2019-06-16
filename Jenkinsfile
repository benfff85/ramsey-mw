pipeline {
    agent { label 'docker' }
    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
    }
    tools {
        maven 'Maven'
        jdk 'Java 10'
    }
    stages {
        stage ('Initialize') {
            steps {
                sh '''
                    echo "PATH = ${PATH}"
                    echo "M2_HOME = ${M2_HOME}"
                '''
            }
        }

        stage('SonarQube') {
            steps {
                script {
                    withSonarQubeEnv('SonarQube') {
                        sh 'mvn clean sonar:sonar'
                    }
                }
            }
        }

        stage ('Maven Compile and Package') {
            steps {
                sh 'mvn clean package'
            }
        }

    }
    post {
        success {
            mail to: 'ben.ferenchak@gmail.com',
            subject: "Successful Pipeline: ${currentBuild.fullDisplayName}",
            body: "Successful build completed ${env.BUILD_URL}"
        }
        failure {
            mail to: 'ben.ferenchak@gmail.com',
            subject: "Failed Pipeline: ${currentBuild.fullDisplayName}",
            body: "Something is wrong with ${env.BUILD_URL}"
        }
    }
}