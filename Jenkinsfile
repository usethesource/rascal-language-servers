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
        stage('Compile and package VSCode extension') {
            sh "npm install vsce; npm rebuild; npm run compile; npm run lsp4j:package; npm install; npm package; vsce package"
        }
    }
  } catch (e) {
    slackSend (color: '#d9534f', message: "FAILED: <${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>")
    throw e
  }
}
