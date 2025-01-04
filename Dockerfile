# Use a Maven base image for building the application
FROM maven:3.9-eclipse-temurin-21 AS build

# Set the working directory
WORKDIR /app

# Copy only the Maven build file for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the source code after caching dependencies
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Use a smaller JRE image for runtime
FROM eclipse-temurin:21-jre-alpine AS final

# Set the working directory
WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/target/ramsey-mw-*.jar /app/ramsey-mw.jar

# Add a non-root user and switch to it
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Expose the port on which the app will run
EXPOSE 8080

# Add a health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Specify the command to run the application with JAVA_OPTS from the environment
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/ramsey-mw.jar"]