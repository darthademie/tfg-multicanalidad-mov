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
                # 1. Usamos la imagen oficial de Python en Alpine (ya trae pip)
                # 2. Instalamos Java (openjdk11) y la herramienta apkleaks
                tar -cf - app-debug.apk | docker run --rm -i python:3.9-alpine sh -c "
                    # Instalamos dependencias del sistema necesarias
                    apk add --no-cache openjdk11-jre unzip git wget
                    
                    # Instalamos apkleaks a traves de pip
                    pip install apkleaks
                    
                    # Extraemos el APK del pipe tar
                    mkdir -p /workspace && cd /workspace
                    tar -xf -
                    
                    echo 'Analizando APK...'
                    # Ejecutamos APKLeaks. 
                    # El argumento --compile fuerza la descarga de jadx si no está,
                    # pero como es un shell interactivo ('-i'), le pasamos un 'yes' por delante
                    yes | apkleaks -f app-debug.apk -o /reporte-mast.txt || true
                    
                    # Devolvemos el reporte empaquetado en tar para esquivar DooD
                    tar -cf - /reporte-mast.txt
                " > reporte.tar
                
                # Desempaquetamos el tar resultante en el workspace de Jenkins
                tar -xf reporte.tar
                """
            }
            post {
                always {
                    echo 'Extrayendo informe de seguridad...'
                    // Guardamos el informe como artefacto (ahora el archivo se llama reporte-mast.txt pero está en la raíz del workspace)
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