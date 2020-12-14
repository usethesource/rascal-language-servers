node {
  env.JAVA_HOME="${tool 'jdk-oracle-8'}"
  env.N_PREFIX="${HOME}/node_installs/"
  env.NPM_CONFIG_PREFIX="${HOME}/npm-libs"
  env.PATH="${env.JAVA_HOME}/bin:${env.NPM_CONFIG_PREFIX}/bin:${env.N_PREFIX}/bin:${env.PATH}"
  env.NPM_VERSION="15.3.0"

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
            sh 'mkdir -p ${N_PREFIX}'
            sh 'mkdir -p ${NPM_CONFIG_PREFIX}'
            sh 'n --version || npm install -g n'
            sh "n ${NPM_VERSION}"
            sh 'npm install -g vsce'
            sh 'npm install'
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
            sh 'vsce package'
        }
    }
  } catch (e) {
    slackSend (color: '#d9534f', message: "FAILED: <${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>")
    throw e
  }
}
