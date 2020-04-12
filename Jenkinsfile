pipeline {
    agent { label 'docker' }
    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
    }
    tools {
        maven 'Maven'
        jdk 'Java 14'
    }

    environment {
      IMAGE = readMavenPom().getArtifactId()
      VERSION = readMavenPom().getVersion()
    }

    stages {
        stage ('Initialize') {
            steps {
                sh '''
                    echo "PATH = ${PATH}"
                    echo "M2_HOME = ${M2_HOME}"
                    echo "IMAGE = ${IMAGE}"
                    echo "VERSION = ${VERSION}"
                '''
            }
        }

        stage ('Maven Compile and Package') {
            steps {
                sh 'mvn clean package'
            }
        }

        stage('SonarQube') {
            steps {
                script {
                    withSonarQubeEnv('SonarQube') {
                        sh 'mvn sonar:sonar'
                    }
                }
            }
        }

        stage ('Docker') {
            steps {
                sh 'cp target/ramsey-mw-${VERSION}.jar target/ramsey-mw.jar'
                sh 'echo ${VERSION} > target/version.txt'
                sh 'find . -ls'
                sh 'docker build -t benferenchak/ramsey-mw:develop .'
                withDockerRegistry([ credentialsId: "docker-hub-credentials", url: "" ]) {
                    sh 'docker push benferenchak/ramsey-mw:develop'
                }
            }
        }
    }
}