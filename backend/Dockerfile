# Imagen del backend DatoPublico (FT00041).
#
# **Multi-stage y sin Android SDK**: el stage `build` compila la distribución de
# `:backend` con `installDist -PskipClient` (excluye `:composeApp`, que exige el
# Android SDK) y el stage `runtime` (JRE 21) copia solo esa distribución y arranca
# el launcher.
#
# **Sin secretos ni modelo** en la imagen: la clave de OpenCode, la contraseña de
# PostgreSQL y el modelo E5 se inyectan en runtime (el modelo, por volumen de solo
# lectura). El contexto de build se reduce con `.dockerignore`.

# --- Stage build: compila la distribución del backend ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY config config
COPY shared shared
COPY backend backend
RUN chmod +x gradlew \
    && ./gradlew :backend:installDist -PskipClient --no-daemon

# --- Stage runtime: solo la JRE y la distribución ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd --create-home --uid 10001 appuser
COPY --from=build /workspace/backend/build/install/backend/ ./
USER appuser
# Puerto interno: el backend NO se publica al host; solo Nginx lo alcanza.
EXPOSE 8080
ENTRYPOINT ["./bin/backend"]
