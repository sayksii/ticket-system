pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    parameters {
        string(
            name: 'BRANCH',
            defaultValue: 'main',
            description: '要 Build 的 Git Branch'
        )

        string(
            name: 'IMAGE_TAG',
            defaultValue: 'auto',
            description: 'auto = Jenkins BUILD_NUMBER + Git short SHA'
        )

        choice(
            name: 'SERVICE',
            choices: ['ALL', 'backend', 'order-worker', 'frontend'],
            description: '要 Build / Push / Deploy 哪個 Application'
        )
    }

    environment {
        REGISTRY       = '10.10.10.20:8858'
        HARBOR_PROJECT = 'devops-test'

        BUILD_HOST     = '10.10.10.20'
        DEPLOY_HOST    = '10.10.10.10'

        NAMESPACE      = 'devops-test'

        // Jenkins Credential IDs
        GITHUB_CREDENTIALS = 'github-pat'
        HARBOR_CREDENTIALS = 'harbor-credential'
        SSH_CREDENTIALS    = 'lab-ssh-key'
    }

    stages {
        stage('Init') {
            steps {
                script {
                    def safeJob = env.JOB_BASE_NAME.replaceAll(/[^A-Za-z0-9_.-]/, '-')
                    env.REMOTE_BUILD_DIR  = "/home/ubuntu/jenkins-builds/${safeJob}-${env.BUILD_NUMBER}"
                    env.REMOTE_DEPLOY_DIR = "/home/ubuntu/jenkins-deploys/${safeJob}-${env.BUILD_NUMBER}"
                    env.REMOTE_BUILD_CREATED  = 'false'
                    env.REMOTE_DEPLOY_CREATED = 'false'
                    currentBuild.description = "${params.SERVICE} ${params.BRANCH}"
                }

                sh '''
                  set -eu
                  echo "BRANCH=${BRANCH}"
                  echo "SERVICE=${SERVICE}"
                  echo "IMAGE_TAG=${IMAGE_TAG}"
                  echo "REMOTE_BUILD_DIR=${REMOTE_BUILD_DIR}"
                  echo "REMOTE_DEPLOY_DIR=${REMOTE_DEPLOY_DIR}"
                '''
            }
        }

        stage('Checkout') {
            steps {
                // Pipeline from SCM 會先把 Jenkinsfile 從 Job 設定的 Branch 載入。
                // 這裡再依 BRANCH Parameter 正式 Checkout 要 Build 的 Source。
                checkout scm

                script {
                    env.REPO_URL = sh(
                        script: 'git config --get remote.origin.url',
                        returnStdout: true
                    ).trim()
                }

                deleteDir()

                script {
                    git(
                        branch: params.BRANCH,
                        credentialsId: env.GITHUB_CREDENTIALS,
                        url: env.REPO_URL
                    )

                    env.GIT_SHORT = sh(
                        script: 'git rev-parse --short=7 HEAD',
                        returnStdout: true
                    ).trim()

                    env.RELEASE_TAG =
                        params.IMAGE_TAG == 'auto'
                        ? "${env.BUILD_NUMBER}-${env.GIT_SHORT}"
                        : params.IMAGE_TAG

                    if (!(env.RELEASE_TAG ==~ /[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}/)) {
                        error("非法 IMAGE_TAG / RELEASE_TAG: ${env.RELEASE_TAG}")
                    }

                    currentBuild.description =
                        "${params.SERVICE} ${env.RELEASE_TAG}"
                }

                sh '''
                  set -eu
                  echo "REPO_URL=${REPO_URL}"
                  echo "COMMIT=$(git rev-parse HEAD)"
                  echo "RELEASE_TAG=${RELEASE_TAG}"
                '''
            }
        }

        stage('Test') {
            steps {
                sh '''
                  set -eu

                  mvn -B -f backend/pom.xml clean test
                  mvn -B -f order-worker/pom.xml clean test
                '''
            }
        }

        stage('Package') {
            steps {
                sh '''
                  set -eu

                  mvn -B -f backend/pom.xml package -DskipTests
                  mvn -B -f order-worker/pom.xml package -DskipTests
                '''

                archiveArtifacts(
                    artifacts: 'backend/target/*.jar,order-worker/target/*.jar',
                    fingerprint: true
                )
            }
        }

        stage('SonarQube - Backend') {
            steps {
                withSonarQubeEnv('sonarqube') {
                    sh '''
                      set -eu

                      mvn -B -f backend/pom.xml \
                        org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                        -Dsonar.projectKey=ticket-backend \
                        -Dsonar.projectName="Ticket Backend"
                    '''
                }
            }
        }

        stage('Quality Gate - Backend') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('SonarQube - Order Worker') {
            steps {
                withSonarQubeEnv('sonarqube') {
                    sh '''
                      set -eu

                      mvn -B -f order-worker/pom.xml \
                        org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                        -Dsonar.projectKey=ticket-order-worker \
                        -Dsonar.projectName="Ticket Order Worker"
                    '''
                }
            }
        }

        stage('Quality Gate - Order Worker') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Prepare SSH') {
            steps {
                sh '''
                  set -eu

                  mkdir -p "$HOME/.ssh"
                  chmod 700 "$HOME/.ssh"
                  touch "$HOME/.ssh/known_hosts"
                  chmod 600 "$HOME/.ssh/known_hosts"

                  if ! ssh-keygen -F "$BUILD_HOST" >/dev/null 2>&1; then
                    ssh-keyscan -T 5 "$BUILD_HOST" >> "$HOME/.ssh/known_hosts"
                  fi

                  if ! ssh-keygen -F "$DEPLOY_HOST" >/dev/null 2>&1; then
                    ssh-keyscan -T 5 "$DEPLOY_HOST" >> "$HOME/.ssh/known_hosts"
                  fi
                '''
            }
        }

        stage('Docker Build & Push') {
            steps {
                script {
                    env.REMOTE_BUILD_CREATED = 'true'
                }

                withCredentials([
                    usernamePassword(
                        credentialsId: env.HARBOR_CREDENTIALS,
                        usernameVariable: 'HARBOR_USER',
                        passwordVariable: 'HARBOR_PASS'
                    )
                ]) {
                    sshagent(credentials: [env.SSH_CREDENTIALS]) {
                        sh '''
                          set -eu

                          ARCHIVE="/tmp/ticket-system-${BUILD_NUMBER}.tar.gz"
                          rm -f "$ARCHIVE"

                          tar \
                            --exclude='.git' \
                            --exclude='*/target' \
                            --exclude='ticket-system-*.zip' \
                            -czf "$ARCHIVE" .

                          ssh -o BatchMode=yes \
                            "ubuntu@${BUILD_HOST}" \
                            "mkdir -p '${REMOTE_BUILD_DIR}'"

                          scp -q \
                            "$ARCHIVE" \
                            "ubuntu@${BUILD_HOST}:${REMOTE_BUILD_DIR}/source.tar.gz"

                          ssh -o BatchMode=yes \
                            "ubuntu@${BUILD_HOST}" \
                            "cd '${REMOTE_BUILD_DIR}' && \
                             tar -xzf source.tar.gz && \
                             rm -f source.tar.gz && \
                             chmod +x ci/part8/remote-build.sh"

                          # Password 只走 stdin，不放在 docker login 參數裡。
                          set +x
                          printf '%s' "$HARBOR_PASS" | \
                            ssh -o BatchMode=yes \
                              "ubuntu@${BUILD_HOST}" \
                              "sudo docker login '${REGISTRY}' \
                                 -u '${HARBOR_USER}' \
                                 --password-stdin"
                          set -x

                          cleanup_registry_login() {
                            ssh -o BatchMode=yes \
                              "ubuntu@${BUILD_HOST}" \
                              "sudo docker logout '${REGISTRY}' >/dev/null 2>&1 || true" \
                              || true
                          }
                          trap cleanup_registry_login EXIT

                          ssh -o BatchMode=yes \
                            "ubuntu@${BUILD_HOST}" \
                            "cd '${REMOTE_BUILD_DIR}' && \
                             ./ci/part8/remote-build.sh \
                               '${REGISTRY}' \
                               '${HARBOR_PROJECT}' \
                               '${RELEASE_TAG}' \
                               '${SERVICE}'"

                          rm -f "$ARCHIVE"
                        '''
                    }
                }
            }
        }

        stage('Prepare Deploy') {
            when {
                expression {
                    return params.BRANCH == 'main'
                }
            }

            steps {
                script {
                    env.REMOTE_DEPLOY_CREATED = 'true'
                }

                sshagent(credentials: [env.SSH_CREDENTIALS]) {
                    sh '''
                      set -eu

                      ssh -o BatchMode=yes \
                        "ubuntu@${DEPLOY_HOST}" \
                        "mkdir -p '${REMOTE_DEPLOY_DIR}'"

                      scp -q \
                        ci/part8/k8s-release.sh \
                        scripts/part8-smoke.sh \
                        "ubuntu@${DEPLOY_HOST}:${REMOTE_DEPLOY_DIR}/"

                      ssh -o BatchMode=yes \
                        "ubuntu@${DEPLOY_HOST}" \
                        "chmod +x \
                          '${REMOTE_DEPLOY_DIR}/k8s-release.sh' \
                          '${REMOTE_DEPLOY_DIR}/part8-smoke.sh'"
                    '''
                }
            }
        }

        stage('Deploy') {
            when {
                expression {
                    return params.BRANCH == 'main'
                }
            }

            steps {
                sshagent(credentials: [env.SSH_CREDENTIALS]) {
                    sh '''
                      set -eu

                      ssh -o BatchMode=yes \
                        "ubuntu@${DEPLOY_HOST}" \
                        "'${REMOTE_DEPLOY_DIR}/k8s-release.sh' \
                          deploy \
                          '${REGISTRY}' \
                          '${HARBOR_PROJECT}' \
                          '${RELEASE_TAG}' \
                          '${SERVICE}' \
                          '${NAMESPACE}'"
                    '''
                }
            }
        }

        stage('Rollout Check') {
            when {
                expression {
                    return params.BRANCH == 'main'
                }
            }

            steps {
                sshagent(credentials: [env.SSH_CREDENTIALS]) {
                    sh '''
                      set -eu

                      ssh -o BatchMode=yes \
                        "ubuntu@${DEPLOY_HOST}" \
                        "'${REMOTE_DEPLOY_DIR}/k8s-release.sh' \
                          rollout \
                          '${REGISTRY}' \
                          '${HARBOR_PROJECT}' \
                          '${RELEASE_TAG}' \
                          '${SERVICE}' \
                          '${NAMESPACE}'"
                    '''
                }
            }
        }

        stage('Smoke Test') {
            when {
                expression {
                    return params.BRANCH == 'main'
                }
            }

            steps {
                sshagent(credentials: [env.SSH_CREDENTIALS]) {
                    sh '''
                      set -eu

                      ssh -o BatchMode=yes \
                        "ubuntu@${DEPLOY_HOST}" \
                        "'${REMOTE_DEPLOY_DIR}/part8-smoke.sh' \
                          '${SERVICE}' \
                          '${NAMESPACE}'"
                    '''
                }
            }
        }

        stage('Show Runtime Images') {
            when {
                expression {
                    return params.BRANCH == 'main'
                }
            }

            steps {
                sshagent(credentials: [env.SSH_CREDENTIALS]) {
                    sh '''
                      set -eu

                      ssh -o BatchMode=yes \
                        "ubuntu@${DEPLOY_HOST}" \
                        "'${REMOTE_DEPLOY_DIR}/k8s-release.sh' \
                          show \
                          '${REGISTRY}' \
                          '${HARBOR_PROJECT}' \
                          '${RELEASE_TAG}' \
                          '${SERVICE}' \
                          '${NAMESPACE}'"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "SUCCESS: ${env.RELEASE_TAG ?: 'no-release-tag'}"
        }

        failure {
            echo 'PIPELINE FAILED：先看第一個變紅的 Stage。'
        }

        always {
            script {
                if (env.REMOTE_BUILD_CREATED == 'true') {
                    sshagent(credentials: [env.SSH_CREDENTIALS]) {
                        sh '''
                          set +e

                          ssh                             -o BatchMode=yes                             -o ConnectTimeout=5                             "ubuntu@${BUILD_HOST}"                             "rm -rf '${REMOTE_BUILD_DIR}'"

                          rm -f "/tmp/ticket-system-${BUILD_NUMBER}.tar.gz"

                          exit 0
                        '''
                    }
                }

                if (env.REMOTE_DEPLOY_CREATED == 'true') {
                    sshagent(credentials: [env.SSH_CREDENTIALS]) {
                        sh '''
                          set +e

                          ssh                             -o BatchMode=yes                             -o ConnectTimeout=5                             "ubuntu@${DEPLOY_HOST}"                             "rm -rf '${REMOTE_DEPLOY_DIR}'"

                          exit 0
                        '''
                    }
                }
            }
        }
    }
}
