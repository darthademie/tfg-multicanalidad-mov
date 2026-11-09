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

        stage('Pipeline Principal (CI/CD)') {
            when {
                expression {
                    // 1. Comprobamos si el pipeline se ha lanzado manualmente (por un usuario dándole al botón)
                    def isManualTrigger = currentBuild.rawBuild.getCauses().any { cause ->
                        cause instanceof hudson.model.Cause.UserIdCause
                    }
                    
                    // Si se ha lanzado a mano, saltamos la comprobación y permitimos la ejecución
                    if (isManualTrigger) {
                        echo "[INFO] Compilación lanzada MANUALMENTE. Ignorando comprobación de '[skip ci]'."
                        return true
                    }

                    // 2. Si NO ha sido manual (ej: viene de un Webhook de GitHub), comprobamos el commit
                    def commitMessage = sh(script: 'git log -1 --pretty=%B', returnStdout: true).trim()
                    if (commitMessage.contains('[skip ci]')) {
                        echo "Se ha detectado '[skip ci]' desde un evento automático. Abortando ejecución."
                        currentBuild.result = 'ABORTED'
                        return false
                    }
                    
                    // Si no es manual y no tiene '[skip ci]', permitimos la ejecución
                    return true
                }
            }
            stages {
                stage('2. Actualizar Versión (GitOps)') {
                    steps {
                        echo 'Actualizando versionCode y versionName en build.gradle...'
                        withCredentials([usernamePassword(credentialsId: env.GH_TOKEN_CREDS, usernameVariable: 'GH_USER', passwordVariable: 'GH_TOKEN')]) {
                            sh """
                            # 1. Configurar identidad de Git
                            git config --global user.email "jenkins@teleupm.com"
                            git config --global user.name "Jenkins CI"
                            
                            # 2. Inyectar la nueva versión usando sed
                            sed -i -E "s/versionCode [0-9]+/versionCode ${env.BUILD_NUMBER}/g" app/build.gradle
                            sed -i -E "s/versionName \\"[^\\"]+\\"/versionName \\"${TAG}\\"/g" app/build.gradle
                            
                            # 3. Hacer Commit y Push al repositorio con la etiqueta mágica
                            git add app/build.gradle
                            git commit -m "gitops: Despliegue automático de versión ${TAG} [skip ci]" || echo "No hay cambios"
                            git push https://\${GH_USER}:\${GH_TOKEN}@github.com/${GH_REPO}.git HEAD:main
                            """
                        }
                    }
                }

                stage('3. Construir APK (Android SDK)') {
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

                stage('4. Análisis de Seguridad (MAST Blackbox)') {
                    steps {
                        echo 'Analizando el APK en busca de secretos, URIs y vulnerabilidades...'
                        sh """
                        tar -cf - app-debug.apk | docker run --name apk-scanner -i python:3.9-alpine sh -c "
                            apk add --no-cache openjdk11-jre unzip git wget
                            pip install apkleaks
                            
                            mkdir -p /workspace && cd /workspace
                            tar -xf -
                            
                            yes | apkleaks -f app-debug.apk -o /reporte-mast.txt || true
                        "
                        """
                    }
                    post {
                        always {
                            echo 'Extrayendo informe de seguridad...'
                            sh """
                            docker cp apk-scanner:/reporte-mast.txt ./reporte-mast.txt || true
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
        }
    }
    post {
        always {
            echo 'Limpiando el workspace de Jenkins...'
            cleanWs()
        }
        success {
            echo "[INFO] Pipeline de Android completado con éxito. Nueva release v${TAG} publicada."
        }
        failure {
            echo "[ERROR] El pipeline ha fallado. Revisa la consola para ver los detalles."
        }
    }
}