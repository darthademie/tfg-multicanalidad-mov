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

        stage('2. Actualizar Versión (GitOps)') {
            steps {
                echo 'Actualizando versionCode y versionName en build.gradle...'
                withCredentials([usernamePassword(credentialsId: env.GH_TOKEN_CREDS, usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                    sh """
                    # 1. Configurar identidad de Git
                    git config --global user.email "jenkins@teleupm.com"
                    git config --global user.name "Jenkins CI"
                    
                    # 2. Inyectar la nueva versión usando sed con expresiones regulares (regex)
                    # Sustituye el número actual por el BUILD_NUMBER de Jenkins
                    sed -i -E "s/versionCode [0-9]+/versionCode ${env.BUILD_NUMBER}/g" app/build.gradle
                    # Sustituye el texto entre comillas por nuestro TAG (ej: "1.25")
                    sed -i -E "s/versionName \\"[^\\"]+\\"/versionName \\"${TAG}\\"/g" app/build.gradle
                    
                    # 3. Hacer Commit y Push al repositorio
                    git add app/build.gradle
                    git commit -m "gitops: Bump version to ${TAG} [skip ci]" || echo "No hay cambios"
                    git push https://\${GH_USER}:\${GH_TOKEN}@github.com/${GH_REPO}.git HEAD:main
                    """
                }
            }
        }

        stage('3. Construir APK (Android SDK)') {
            steps {
                echo 'Levantando contenedor con SDK de Android y compilando...'
                sh """
                # La compilación ahora usará el build.gradle que acabamos de modificar
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

        stage('4. Análisis de Seguridad (MAST Blackbox)') {
            steps {
                echo 'Analizando el APK en busca de secretos, URIs y vulnerabilidades...'
                sh """
                # Usamos un contenedor con nombre fijo (apk-scanner) y SIN --rm para que no se autodestruya
                tar -cf - app-debug.apk | docker run --name apk-scanner -i python:3.9-alpine sh -c "
                    # Instalamos dependencias
                    apk add --no-cache openjdk11-jre unzip git wget
                    pip install apkleaks
                    
                    # Extraemos APK
                    mkdir -p /workspace && cd /workspace
                    tar -xf -
                    
                    # Ejecutamos el análisis
                    yes | apkleaks -f app-debug.apk -o /reporte-mast.txt || true
                "
                """
            }
            post {
                always {
                    echo 'Extrayendo informe de seguridad...'
                    sh """
                    # Rescatamos el informe con docker cp
                    docker cp apk-scanner:/reporte-mast.txt ./reporte-mast.txt || true
                    # Ahora sí destruimos el contenedor
                    docker rm apk-scanner || true
                    """
                    archiveArtifacts artifacts: 'reporte-mast.txt', allowEmptyArchive: true
                }
            }
        }

        stage('5. Publicar en GitHub Releases') {
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