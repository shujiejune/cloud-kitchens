#!/usr/bin/env groovy

// All 7 microservices — built, imaged, and deployed in order
def SERVICES = [
    'config-server',
    'eureka-server',
    'api-gateway',
    'auth-service',
    'catalog-service',
    'procurement-service',
    'orders-service'
]

pipeline {
    agent any   // Jenkins agent must have: JDK 17, Docker, AWS CLI

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 45, unit: 'MINUTES')
        timestamps()
    }

    environment {
        AWS_REGION      = 'us-east-1'
        ECR_REGISTRY    = credentials('ecr-registry-url')   // plain-text credential: <account>.dkr.ecr.<region>.amazonaws.com
        ECS_CLUSTER_STG = 'cloudkitchens-staging'
        ECS_CLUSTER_PRD = 'cloudkitchens-production'
        // Unique container names per build to allow parallel runs on the same agent
        DB_CONTAINER    = "ck-mysql-${BUILD_NUMBER}"
        MONGO_CONTAINER = "ck-mongo-${BUILD_NUMBER}"
        REDIS_CONTAINER = "ck-redis-${BUILD_NUMBER}"
    }

    stages {

        // ── 1. Checkout ────────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.IMAGE_TAG = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    echo "Building image tag: ${env.IMAGE_TAG}"
                }
            }
        }

        // ── 2. Build ───────────────────────────────────────────────────────────
        stage('Build') {
            steps {
                sh './mvnw -DskipTests --no-transfer-progress clean package'
            }
        }

        // ── 3. Test ────────────────────────────────────────────────────────────
        // Spins up MySQL, MongoDB, and Redis as Docker containers,
        // runs `mvn verify`, then tears them down regardless of outcome.
        stage('Test') {
            steps {
                script {
                    sh """
                        docker run -d --name ${DB_CONTAINER} \
                            -e MYSQL_ROOT_PASSWORD=testpassword \
                            -e MYSQL_DATABASE=cloudkitchens_db \
                            -p 3307:3306 mysql:8.0

                        docker run -d --name ${MONGO_CONTAINER} \
                            -p 27017:27017 mongo:6

                        docker run -d --name ${REDIS_CONTAINER} \
                            -p 6379:6379 redis:7-alpine

                        timeout 60 bash -c \
                            'until docker exec ${DB_CONTAINER} mysqladmin ping -h localhost --silent; do sleep 2; done'
                    """
                    try {
                        sh '''
                            ./mvnw --no-transfer-progress verify \
                                -DSPRING_DATASOURCE_URL=jdbc:mysql://localhost:3307/cloudkitchens_db \
                                -DSPRING_DATASOURCE_USERNAME=root \
                                -DSPRING_DATASOURCE_PASSWORD=testpassword \
                                -DSPRING_DATA_MONGODB_URI=mongodb://localhost:27017/cloudkitchens_procurement \
                                -DSPRING_DATA_REDIS_HOST=localhost \
                                -DJWT_SECRET=test-256-bit-secret-key-for-ci-pipeline
                        '''
                    } finally {
                        sh "docker rm -f ${DB_CONTAINER} ${MONGO_CONTAINER} ${REDIS_CONTAINER} || true"
                    }
                }
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: '**/target/surefire-reports/**', allowEmptyArchive: true
                }
            }
        }

        // ── 4. Docker Build ────────────────────────────────────────────────────
        stage('Docker Build') {
            when { branch 'main' }
            steps {
                script {
                    SERVICES.each { svc ->
                        sh """
                            docker build \
                                -t ${ECR_REGISTRY}/cloudkitchens/${svc}:${IMAGE_TAG} \
                                -t ${ECR_REGISTRY}/cloudkitchens/${svc}:latest \
                                ./${svc}
                        """
                    }
                }
            }
        }

        // ── 5. Push to ECR ─────────────────────────────────────────────────────
        stage('Push to ECR') {
            when { branch 'main' }
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-credentials'
                ]]) {
                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REGISTRY}"
                    script {
                        SERVICES.each { svc ->
                            sh """
                                docker push ${ECR_REGISTRY}/cloudkitchens/${svc}:${IMAGE_TAG}
                                docker push ${ECR_REGISTRY}/cloudkitchens/${svc}:latest
                            """
                        }
                    }
                }
            }
        }

        // ── 6. Deploy to Staging ───────────────────────────────────────────────
        stage('Deploy to Staging') {
            when { branch 'main' }
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-credentials'
                ]]) {
                    script {
                        SERVICES.each { svc ->
                            sh """
                                aws ecs update-service \
                                    --region ${AWS_REGION} \
                                    --cluster ${ECS_CLUSTER_STG} \
                                    --service ${svc} \
                                    --force-new-deployment
                            """
                        }
                        // Block until all staging tasks reach RUNNING state
                        SERVICES.each { svc ->
                            sh """
                                aws ecs wait services-stable \
                                    --region ${AWS_REGION} \
                                    --cluster ${ECS_CLUSTER_STG} \
                                    --services ${svc}
                            """
                        }
                    }
                }
            }
        }

        // ── 7. Smoke Tests ─────────────────────────────────────────────────────
        stage('Smoke Tests') {
            when { branch 'main' }
            steps {
                withCredentials([string(credentialsId: 'staging-gateway-url', variable: 'STAGING_URL')]) {
                    sh """
                        curl -sf \${STAGING_URL}/actuator/health
                        curl -sf \${STAGING_URL}/api/v1/catalog/actuator/health
                        curl -sf \${STAGING_URL}/api/v1/procurement/actuator/health
                        curl -sf \${STAGING_URL}/api/v1/orders/actuator/health
                    """
                }
            }
        }

        // ── 8. Manual Approval Gate ────────────────────────────────────────────
        stage('Approve Production Deploy') {
            when { branch 'main' }
            steps {
                timeout(time: 24, unit: 'HOURS') {
                    input message: "Deploy image ${IMAGE_TAG} to production?", ok: 'Deploy'
                }
            }
        }

        // ── 9. Deploy to Production ────────────────────────────────────────────
        // ECS rolling update: replaces one task at a time; ALB drains connections
        // before termination. Rollback = re-trigger with a previous IMAGE_TAG.
        stage('Deploy to Production') {
            when { branch 'main' }
            steps {
                withCredentials([[
                    $class: 'AmazonWebServicesCredentialsBinding',
                    credentialsId: 'aws-credentials'
                ]]) {
                    script {
                        SERVICES.each { svc ->
                            sh """
                                aws ecs update-service \
                                    --region ${AWS_REGION} \
                                    --cluster ${ECS_CLUSTER_PRD} \
                                    --service ${svc} \
                                    --force-new-deployment
                            """
                        }
                        SERVICES.each { svc ->
                            sh """
                                aws ecs wait services-stable \
                                    --region ${AWS_REGION} \
                                    --cluster ${ECS_CLUSTER_PRD} \
                                    --services ${svc}
                            """
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            // Safety net: remove test containers if the Test stage left them behind
            sh "docker rm -f ${DB_CONTAINER} ${MONGO_CONTAINER} ${REDIS_CONTAINER} || true"
            cleanWs()
        }
        failure {
            echo "Build ${BUILD_NUMBER} (${env.IMAGE_TAG ?: 'unknown'}) failed — check the stage logs above."
        }
    }
}
