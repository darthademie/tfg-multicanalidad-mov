pipeline {
    agent any 

    environment {
        GH_TOKEN_CREDS = 'github-darthademie'
        TAG = "1.${env.BUILD_NUMBER}"
        GH_REPO = 'darthademie/tfg-multicanalidad-mov'
    }

    stages {
        stage('1. Obtener Código') {
            steps {
                echo 'Descargando código fuente de Android...'
                git branch: 'main', url: "https://github.com/${GH_REPO}.git"
            }
        }

        stage('2. Construir APK (Android SDK)') {
            steps {
                echo 'Levantando contenedor con SDK de Android y compilando...'
                sh """
                tar -cf - . | docker run --name android-builder -i mingc/android-build-box:latest bash -c "
                    mkdir -p /workspace && cd /workspace 
                    tar -xf - 
                    chmod +x ./gradlew 
                    ./gradlew assembleDebug
                "
                """
            }
            post {
                always {
                    echo 'Extrayendo APK del contenedor...'
                    sh """
                    docker cp android-builder:/workspace/app/build/outputs/apk/debug/app-debug.apk ./app-debug.apk || true
                    docker rm android-builder || true
                    """
                    archiveArtifacts artifacts: 'app-debug.apk', allowEmptyArchive: true
                }
            }
        }

        stage('3. Análisis de Seguridad (MAST Blackbox)') {
            steps {
                echo 'Destripando el APK en busca de secretos, URIs y vulnerabilidades...'
                sh """
                # Inyectamos el APK compilado en la imagen oficial de apkleaks
                # Sobrescribimos el entrypoint con 'sh' para usar nuestra tubería tar y esquivar el DooD
                tar -cf - app-debug.apk | docker run --name apk-scanner --entrypoint sh -i dwisiswant0/apkleaks:latest -c "
                    mkdir -p /workspace && cd /workspace
                    tar -xf -
                    
                    echo 'Analizando APK...'
                    # Ejecutamos APKLeaks y le pedimos que genere el reporte en txt
                    apkleaks -f app-debug.apk -o reporte-mast.txt
                "
                """
            }
            post {
                always {
                    echo 'Extrayendo informe de seguridad...'
                    sh """
                    # Rescatamos el informe .txt a Jenkins y borramos el contenedor
                    docker cp apk-scanner:/workspace/reporte-mast.txt ./reporte-mast.txt || true
                    docker rm apk-scanner || true
                    """
                    // Guardamos el informe como artefacto para poder descargarlo desde la web de Jenkins
                    archiveArtifacts artifacts: 'reporte-mast.txt', allowEmptyArchive: true
                }
            }
        }

        stage('4. Publicar en GitHub Releases') {
            steps {
                echo 'Creando Release en GitHub y subiendo el APK...'
                withCredentials([usernamePassword(credentialsId: env.GH_TOKEN_CREDS, usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    sh """
                    tar -cf - app-debug.apk | docker run --rm -i -e GH_TOKEN=\${GH_TOKEN} alpine:latest sh -c "
                        apk add --no-cache github-cli && \\
                        tar -xf - && \\
                        gh release create v${TAG} app-debug.apk \\
                            --repo ${GH_REPO} \\
                            --title 'Versión de Desarrollo v${TAG}' \\
                            --notes 'APK autogenerado por Jenkins CI (Debug Build)'
                    "
                    """
                }
            }
        }
    }
    post {
        always {
            cleanWs()
        }
    }
}