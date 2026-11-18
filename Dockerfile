# 1. Фронтенд
FROM node:22-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY openapi.yaml /app/openapi.yaml
COPY frontend/ ./
RUN npm run build

# 2. Бэкенд (раздаёт собранный фронтенд как статику)
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app/backend
COPY backend/ ./
COPY --from=frontend /app/frontend/dist src/main/resources/static
RUN ./gradlew --no-daemon bootJar -x test

# 3. Рантайм
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/backend/build/libs/*.jar app.jar
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
