pipeline {
    agent any
    options {
        timestamps()
        disableConcurrentBuilds()
    }
    parameters {
        string(name: 'LOCAL_SOURCE_PATH', defaultValue: 'C:/Users/vinod/Bankingapp', description: 'Project folder accessible to this Windows Jenkins agent')
        string(
            name: 'DOCKER_REGISTRY',
            defaultValue: 'docker.io',
            description: 'Docker registry host, for example docker.io or registry.example.com'
        )
        string(
            name: 'IMAGE_NAMESPACE',
            defaultValue: 'banking',
            description: 'Registry account or organization that owns the banking image repositories'
        )
    }
    environment {
        REGISTRY_CREDENTIALS = 'dockerhub-credentials'
        KUBECONFIG_CREDENTIALS = 'kubeconfig'
        PATH = "C:/Users/vinod/AppData/Local/Programs/DockerDesktop/resources/bin;${env.PATH}"
        DOCKER_CLI = 'C:/Users/vinod/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe'
        IMAGE_TAG = "${BUILD_NUMBER}"
    }
    stages {
        stage('Checkout Source') {
            steps {
                deleteDir()
                script {
                    def sourcePath = params.LOCAL_SOURCE_PATH?.trim()
                    if (!sourcePath) {
                        sourcePath = 'C:/Users/vinod/Bankingapp'
                    }
                    withEnv(["LOCAL_SOURCE_PATH=${sourcePath}"]) {
                        powershell '''
                            $ErrorActionPreference = 'Stop'
                            $source = $env:LOCAL_SOURCE_PATH
                            if (-not (Test-Path -LiteralPath (Join-Path $source 'backend/pom.xml'))) {
                                throw "Project source was not found at '$source'. Set LOCAL_SOURCE_PATH to the project folder accessible to this Jenkins agent."
                            }
                            Get-ChildItem -LiteralPath $source -Force |
                                Where-Object { $_.Name -notin @('.git', 'node_modules', 'dist', 'target', '.env') } |
                                Copy-Item -Destination (Get-Location).Path -Recurse -Force
                        '''
                    }
                }
            }
        }
        stage('Verify Workspace') {
            steps {
                script {
                    if (!fileExists('backend/pom.xml')) {
                        error('Project files are missing from the workspace. Configure this job as "Pipeline script from SCM" and point it at this repository and Jenkinsfile.')
                    }
                }
            }
        }
        stage('Prepare Maven') {
            steps {
                powershell '''
                    $ErrorActionPreference = 'Stop'
                    if (-not $env:JENKINS_HOME) {
                        throw 'JENKINS_HOME is not set for this Jenkins agent.'
                    }
                    $version = '3.9.9'
                    $tools = Join-Path $env:JENKINS_HOME 'tools'
                    $mavenHome = Join-Path $tools "apache-maven-$version"
                    $mavenCommand = Join-Path $mavenHome 'bin/mvn.cmd'
                    if (-not (Test-Path -LiteralPath $mavenCommand)) {
                        New-Item -ItemType Directory -Path $tools -Force | Out-Null
                        $archive = Join-Path $env:TEMP "apache-maven-$version-bin.zip"
                        $url = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$version/apache-maven-$version-bin.zip"
                        & curl.exe --fail --location --silent --show-error --output $archive $url
                        if ($LASTEXITCODE -ne 0) {
                            throw "Maven download failed with exit code $LASTEXITCODE."
                        }
                        Expand-Archive -LiteralPath $archive -DestinationPath $tools -Force
                        Remove-Item -LiteralPath $archive -Force
                    }
                '''
                powershell '& "$env:JENKINS_HOME/tools/apache-maven-3.9.9/bin/mvn.cmd" -version; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }'
            }
        }
        stage('Verify Tools') {
            steps {
                bat 'where.exe java'
                bat 'where.exe node'
                bat 'where.exe npm'
                bat 'where.exe kubectl'
                bat 'java -version'
                bat 'node --version'
                bat 'npm.cmd --version'
                bat 'kubectl version --client'
            }
        }
        stage('Test') {
            steps {
                powershell '& "$env:JENKINS_HOME/tools/apache-maven-3.9.9/bin/mvn.cmd" -B -f backend/pom.xml verify; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }'
                bat 'npm.cmd ci'
                bat 'npm.cmd run build'
            }
        }
        stage('Build and Push Images') {
            options {
                timeout(time: 2, unit: 'MINUTES')
            }
            steps {
                script {
                    withCredentials([usernamePassword(
                        credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'REGISTRY_USER',
                        passwordVariable: 'REGISTRY_PASSWORD'
                    )]) {
                        try {
                            powershell '''
                                $docker = $env:DOCKER_CLI
                                & $docker info
                                if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
                                $env:REGISTRY_PASSWORD | & $docker login $env:DOCKER_REGISTRY --username $env:REGISTRY_USER --password-stdin
                                if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
                                $services = @('service-registry', 'api-gateway', 'customer-service', 'account-service', 'frontend')
                                foreach ($service in $services) {
                                    if ($service -eq 'frontend') {
                                        $dockerfile = 'Dockerfile'
                                    } else {
                                        $dockerfile = "backend/$service/Dockerfile"
                                    }
                                    $image = "$env:DOCKER_REGISTRY/$env:IMAGE_NAMESPACE/$($service):$env:IMAGE_TAG"
                                    & $docker build -f $dockerfile -t $image .
                                    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
                                    & $docker push $image
                                    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
                                }
                            '''
                        } finally {
                            bat(returnStatus: true, script: '"%DOCKER_CLI%" logout "%DOCKER_REGISTRY%"')
                        }
                    }
                }
            }
        }
        stage('Deploy to Kubernetes') {
            steps {
                withCredentials([file(credentialsId: "${KUBECONFIG_CREDENTIALS}", variable: 'KUBECONFIG')]) {
                    bat 'kubectl apply -f k8s\\namespace.yaml'
                    bat 'kubectl apply -f k8s\\config.yaml'
                    bat 'kubectl apply -f k8s\\postgres.yaml'
                    bat 'kubectl -n banking rollout status deployment/postgres --timeout=180s'

                    bat 'kubectl apply -f k8s\\service-registry.yaml'
                    bat 'kubectl apply -f k8s\\customer-service.yaml'
                    bat 'kubectl apply -f k8s\\account-service.yaml'
                    bat 'kubectl apply -f k8s\\api-gateway.yaml'
                    bat 'kubectl apply -f k8s\\frontend.yaml'
                    bat 'kubectl apply -f k8s\\ingress.yaml'

                    bat 'kubectl -n banking set image deployment/service-registry service-registry=%DOCKER_REGISTRY%/%IMAGE_NAMESPACE%/service-registry:%IMAGE_TAG%'
                    bat 'kubectl -n banking set image deployment/customer-service customer-service=%DOCKER_REGISTRY%/%IMAGE_NAMESPACE%/customer-service:%IMAGE_TAG%'
                    bat 'kubectl -n banking set image deployment/account-service account-service=%DOCKER_REGISTRY%/%IMAGE_NAMESPACE%/account-service:%IMAGE_TAG%'
                    bat 'kubectl -n banking set image deployment/api-gateway api-gateway=%DOCKER_REGISTRY%/%IMAGE_NAMESPACE%/api-gateway:%IMAGE_TAG%'
                    bat 'kubectl -n banking set image deployment/frontend frontend=%DOCKER_REGISTRY%/%IMAGE_NAMESPACE%/frontend:%IMAGE_TAG%'

                    bat 'kubectl -n banking rollout status deployment/service-registry --timeout=180s'
                    bat 'kubectl -n banking rollout status deployment/customer-service --timeout=180s'
                    bat 'kubectl -n banking rollout status deployment/account-service --timeout=180s'
                    bat 'kubectl -n banking rollout status deployment/api-gateway --timeout=180s'
                    bat 'kubectl -n banking rollout status deployment/frontend --timeout=180s'

                    script {
                        def nodeIp = powershell(
                            returnStdout: true,
                            script: '''
                                $ErrorActionPreference = 'Stop'
                                $json = kubectl get nodes -o json
                                if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
                                $nodes = $json | ConvertFrom-Json
                                $addresses = $nodes.items[0].status.addresses
                                $node = $addresses | Where-Object { $_.type -eq 'ExternalIP' } | Select-Object -First 1
                                if (-not $node) {
                                    $node = $addresses | Where-Object { $_.type -eq 'InternalIP' } | Select-Object -First 1
                                }
                                if (-not $node) {
                                    Write-Error 'Could not determine a Kubernetes node IP.'
                                    exit 1
                                }
                                Write-Output $node.address
                            '''
                        ).trim()

                        echo "Deployment succeeded. Open these URLs from a network that can reach Kubernetes node ${nodeIp}:"
                        echo "Frontend:         http://${nodeIp}:30080"
                        echo "Eureka dashboard: http://${nodeIp}:30761"
                        echo "API gateway:      http://${nodeIp}:30800"
                        echo "Customer API:     http://${nodeIp}:30801/api/customers"
                        echo "Account API:      http://${nodeIp}:30802/api/accounts"
                    }
                }
            }
        }
    }
}
