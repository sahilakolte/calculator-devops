pipeline {
    agent any

    triggers {
        // GitHub webhook (needs Jenkins reachable from GitHub, e.g. via ngrok).
        githubPush()
        // Fallback: poll GitHub every ~2 minutes in case the webhook can't reach us.
        pollSCM('H/2 * * * *')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15'))
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        // Jenkins credential of type "Username with password" (Docker Hub username + access token).
        // Provides DOCKERHUB_USR and DOCKERHUB_PSW; never hard-coded in the repo.
        DOCKERHUB    = credentials('dockerhub-creds')
        IMAGE_NAME   = "${DOCKERHUB_USR}/calculator-api"
        IMAGE_TAG    = "${BUILD_NUMBER}"
        HOST_PORT    = '8090'
        NOTIFY_EMAIL = 'sahilakolte@gmail.com'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --oneline'
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn -B test'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: false
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t "$IMAGE_NAME:$IMAGE_TAG" -t "$IMAGE_NAME:latest" .'
            }
        }

        stage('Push') {
            steps {
                sh '''
                    echo "$DOCKERHUB_PSW" | docker login -u "$DOCKERHUB_USR" --password-stdin
                    docker push "$IMAGE_NAME:$IMAGE_TAG"
                    docker push "$IMAGE_NAME:latest"
                '''
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        stage('Deploy') {
            steps {
                // Same compose project name every run, so a re-run replaces the
                // running containers in place instead of starting a second stack.
                sh '''
                    docker compose -p calculator up -d --no-build --remove-orphans --wait --wait-timeout 120
                    docker compose -p calculator ps
                '''
                // Verify the deployment end-to-end through the Nginx proxy.
                sh '''
                    for i in $(seq 1 20); do
                        if curl -fs "http://localhost:$HOST_PORT/actuator/health" | grep -q '"status":"UP"'; then
                            echo "Health check passed"
                            curl -fs "http://localhost:$HOST_PORT/api/v1/add?a=2&b=3"; echo
                            exit 0
                        fi
                        sleep 3
                    done
                    echo "Health check failed"
                    exit 1
                '''
            }
        }
    }

    post {
        always {
            // Remove dangling image layers left behind by rebuilds.
            sh 'docker image prune -f || true'
        }
        success {
            emailext(
                to: "${NOTIFY_EMAIL}",
                subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Build ${env.BUILD_NUMBER} of ${env.JOB_NAME} succeeded.

Image: ${env.IMAGE_NAME}:${env.IMAGE_TAG}
App:   http://localhost:${env.HOST_PORT}/api/v1/add?a=2&b=3
Build: ${env.BUILD_URL}"""
            )
        }
        failure {
            emailext(
                to: "${NOTIFY_EMAIL}",
                subject: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                body: """Build ${env.BUILD_NUMBER} of ${env.JOB_NAME} failed.

Console output: ${env.BUILD_URL}console""",
                attachLog: true
            )
        }
    }
}
