pipeline {
    agent any 

    environment {
        // Token personal de GitHub con permisos de 'repo' para poder crear releases
        GH_TOKEN_CREDS = 'github-token-darthademie'
        // Definimos la versión
        TAG = "1.${env.BUILD_NUMBER}"
        // Repositorio para la herramienta de GitHub CLI
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
                # 1. Empaquetamos el código y lo enviamos al contenedor oficial de Android
                # Usamos un contenedor nombrado para poder extraer el APK después
                tar -cf - . | docker run --name android-builder -i mingc/android-build-box:latest bash -c "
                    mkdir -p /workspace && cd /workspace 
                    tar -xf - 
                    chmod +x ./gradlew 
                    # Ejecutamos la compilación Debug
                    ./gradlew assembleDebug
                "
                """
            }
            post {
                always {
                    echo 'Extrayendo APK del contenedor...'
                    sh """
                    # 2. Rescatamos el APK desde la ruta estándar de Gradle hacia el workspace de Jenkins
                    docker cp android-builder:/workspace/app/build/outputs/apk/debug/app-debug.apk ./app-debug.apk || true
                    
                    # 3. Destruimos el contenedor pesado de compilación
                    docker rm android-builder || true
                    """
                    
                    // Guardamos el APK en Jenkins por si la subida a GitHub falla
                    archiveArtifacts artifacts: 'app-debug.apk', allowEmptyArchive: true
                }
            }
        }

        stage('3. Publicar en GitHub Releases') {
            steps {
                echo 'Creando Release en GitHub y subiendo el APK...'
                withCredentials([string(credentialsId: env.GH_TOKEN_CREDS, variable: 'GH_TOKEN')]) {
                    sh """
                    # Usamos el contenedor oficial de GitHub CLI (gh)
                    # Le pasamos el APK por tubería para esquivar el problema DooD
                    tar -cf - app-debug.apk | docker run --rm -i -e GH_TOKEN=\${GH_TOKEN} ghcr.io/cli/cli bash -c "
                        tar -xf -
                        # Creamos la release adjuntando el archivo
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