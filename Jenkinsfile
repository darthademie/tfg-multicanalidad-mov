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
                # 1. Creamos el contenedor. 
                # Añadimos '-i' (Interactive) para mantener abierta la entrada de teclado (STDIN)
                docker create -i --name apk-scanner dwisiswant0/apkleaks:latest -f /app-debug.apk -o /reporte-mast.txt
                
                # 2. Inyectamos el APK dentro del contenedor inactivo
                docker cp ./app-debug.apk apk-scanner:/app-debug.apk
                
                # 3. Arrancamos adjuntando la terminal (-i) y le inyectamos un 'Y' automático
                # Esto simula que un humano ha pulsado la tecla Y y el Intro al saltar el prompt,
                # Y permitirá la instalación de la biblioteca jadx de forma automática
                echo 'Y' | docker start -i apk-scanner
                """
            }
            post {
                always {
                    echo 'Extrayendo informe de seguridad...'
                    sh """
                    # Rescatamos el informe .txt a Jenkins y borramos el contenedor
                    docker cp apk-scanner:/reporte-mast.txt ./reporte-mast.txt || true
                    docker rm apk-scanner || true
                    """
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