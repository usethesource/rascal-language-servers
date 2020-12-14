node {
  env.JAVA_HOME="${tool 'jdk-oracle-8'}"
  env.PATH="${env.JAVA_HOME}/bin:${env.PATH}"
  env.N_PREFIX="${HOME}/node_installs/"
  env.NPM_CONFIG_PREFIX="${HOME}/npm-libs"
  env.NPM_VERSION="15.4.0"
  env.NPM="${N_PREFIX}/bin/npm"
  env.VSCE="${N_PREFIX}/bin/vsce"

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
            sh 'npm install -g n'
            sh "${NPM_CONFIG_PREFIX}/bin/n ${NPM_VERSION}"
            sh '${NPM} install webpack'
            sh '${NPM} install vsce'
        }

        stage('Rebuild dependencies') {
            sh '${NPM} rebuild'
        }

        stage('Compile VScode extension') {
            sh '${NPM} run compile'
        }

        stage('Copy LSP server jar') {
            sh 'mkdir -p dist'
            sh '${NPM} run lsp4j:package'
        }

        stage('Package VScode extension') {
            sh '${NPM} install'
            sh '${VCSE} package'
        }
    }
  } catch (e) {
    slackSend (color: '#d9534f', message: "FAILED: <${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>")
    throw e
  }
}
