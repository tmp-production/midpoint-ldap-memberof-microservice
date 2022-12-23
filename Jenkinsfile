pipeline {
  environment {
    registry = "titrom/connector"
    registryCredential = 'dockerhub'
    DOCKER_BUILDKIT = '1'
    dockerImage = ''
    dockerImageLatest = ''
  }
  agent any
  stages {
    stage('Building image') {
      steps{
        script {
          dockerImage = docker.build registry + ":$BUILD_NUMBER"
          dockerImageLatest = docker.build registry + ":latest"
        }
      }
    }
    stage('Push to DockerHub') {
      steps {
         script {
            docker.withRegistry( '', registryCredential ) {
            dockerImage.push()
            dockerImageLatest.push()
          }
        }
      }
    }
  }
}