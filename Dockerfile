# 1. Base image JDK 21
FROM openjdk:21-jdk-slim

# 2. Set working directory
WORKDIR /app

# 3. Copy jar file (đã build trước)
COPY target/*.jar app.jar

# 4. Expose port (Render sẽ dùng biến $PORT)
EXPOSE 8080

# 5. Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
