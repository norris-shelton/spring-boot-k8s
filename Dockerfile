FROM maven:3.9-amazoncorretto-21 as builder
WORKDIR /app
COPY pom.xml .
COPY src src

# Build the application
RUN mvn clean package -DskipTests

# Extract the layers
RUN mkdir -p target/extracted && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# Add Datadog agent layer
FROM datadog/agent:latest as datadog

# Add Splunk Universal Forwarder layer
FROM splunk/universalforwarder:latest as splunk-forwarder

# Build the final image using layering
FROM amazoncorretto:21-alpine
WORKDIR /app

# Copy layers from the builder stage in the correct order for optimal caching
# Dependencies layer (changes less frequently)
COPY --from=builder /app/target/extracted/dependencies/ ./
COPY --from=builder /app/target/extracted/spring-boot-loader/ ./
COPY --from=builder /app/target/extracted/snapshot-dependencies/ ./
# Application layer (changes most frequently)
COPY --from=builder /app/target/extracted/application/ ./

# Copy Datadog agent from the datadog stage
COPY --from=datadog /opt/datadog-agent /opt/datadog-agent
COPY --from=datadog /etc/datadog-agent /etc/datadog-agent

# Add Datadog Java agent
RUN mkdir -p /opt/datadog && \
    wget -O /opt/datadog/dd-java-agent.jar https://dtdg.co/latest-java-tracer

# Copy Splunk Universal Forwarder from the splunk-forwarder stage
COPY --from=splunk-forwarder /opt/splunk /opt/splunk
COPY --from=splunk-forwarder /opt/splunkforwarder /opt/splunkforwarder 2>/dev/null || true

# Create necessary directories for Splunk logs
RUN mkdir -p /var/log/splunk && \
    mkdir -p /var/opt/splunk

# Set up Datadog environment variables
ENV DD_APM_ENABLED=true \
    DD_LOGS_ENABLED=true \
    DD_LOGS_CONFIG_CONTAINER_COLLECT_ALL=true \
    DD_JMXFETCH_ENABLED=true \
    DD_PROCESS_AGENT_ENABLED=true

# Set up Splunk environment variables
ENV SPLUNK_START_ARGS=--accept-license \
    SPLUNK_HOME=/opt/splunk \
    SPLUNK_GROUP=splunk \
    SPLUNK_USER=root \
    SPLUNK_FORWARD_SERVER=splunkindexer:9997 \
    SPLUNK_ADD=monitor /app/logs \
    SPLUNK_ADD_1=monitor /var/log/\*

# Set the entrypoint with Datadog Java agent
ENTRYPOINT ["java", "-javaagent:/opt/datadog/dd-java-agent.jar", "-Ddd.service.name=spring-boot-k8s", "-Ddd.profiling.enabled=true", "org.springframework.boot.loader.JarLauncher"]

# Expose the application port
EXPOSE 8080

# Expose Splunk forwarder ports
EXPOSE 8089 8088 1514

# Add health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -q --spider http://localhost:8080/health || exit 1
