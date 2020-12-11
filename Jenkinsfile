node {
  env.JAVA_HOME="${tool 'jdk-oracle-8'}"
  env.PATH="${env.JAVA_HOME}/bin:${env.PATH}"
  try {
    stage('Clone'){
      checkout scm
    }
   
    stage('Compile Rascal LSP server') {
        sh "cd rascal-lsp; mvn package"
    }

    stage('Compile Visual Studio Code extension') {
        sh "npm install vsce; npm rebuild; npm run lsp4j:package; npm install; vsce package"
    }

  } catch (e) {
    slackSend (color: '#d9534f', message: "FAILED: <${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>")
    throw e
  }
}
