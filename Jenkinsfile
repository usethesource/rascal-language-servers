node {
  env.JAVA_HOME="${tool 'jdk-oracle-8'}"
  env.PATH="${env.JAVA_HOME}/bin:${env.PATH}"
  try {
    stage('Clone'){
      checkout scm
    }
 
    dir('rascal-lsp'){
        stage('Compile Rascal LSP') {
          sh "mvn clean compile"
        }

        stage('Package Rascal LSP') {
          sh "mvn package"
        }
    }

    dir ('rascal-vscode-extension') {
        stage('Install prerequisites') {
            sh 'npm install webpack'
            sh 'npm install vsce'
        }

        stage('Rebuild dependencies') {
            sh 'npm rebuild'
        }

        stage('Compile VScode extension') {
            sh 'npm run compile'
        }

        stage('Copy LSP server jar') {
            sh 'mkdir -p dist'
            sh 'npm run lsp4j:package'
        }

        stage('Package VScode extension') {
            sh 'npm install'
            sh 'vsce package'
        }
    }
  } catch (e) {
    slackSend (color: '#d9534f', message: "FAILED: <${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>")
    throw e
  }
}
