# Spring Boot 3 MVC Application with Kubernetes Deployment Guide

This document provides instructions for building and deploying a Spring Boot 3 MVC application to Kubernetes using containerless build methods (Jib/buildpacks) with AWS Corretto 21 and Datadog integration.

## Project Overview

This project is a Spring Boot 3 MVC application configured for:
- Java 21 (Amazon Corretto)
- Containerless builds using Jib or Spring Boot's buildpacks support
- Kubernetes deployment
- Datadog monitoring and observability

## Building the Application without Docker

This project is configured to build container images without requiring a Docker daemon or Docker license, using either:

### Option 1: Using Google Jib

Jib builds optimized container images for Java applications without requiring Docker:

```bash
# Build the image locally
./mvnw clean package jib:build -DskipTests

# To build to a specific registry
./mvnw clean package jib:build -DskipTests -Djib.to.image=your-registry/spring-boot-k8s:latest
```

### Option 2: Using Spring Boot Buildpacks

Spring Boot can build images using Cloud Native Buildpacks:

```bash
# Build the image
./mvnw spring-boot:build-image -DskipTests
```

## Datadog Integration

The application is configured with Datadog for monitoring and observability:

1. The Datadog Java agent is included in the container image
2. JVM flags are configured to enable APM, profiling, and logs
3. Environment variables are set for proper Datadog integration
4. Kubernetes manifests include Datadog annotations and environment variables

## Kubernetes Deployment

The `k8s` directory contains all necessary Kubernetes manifests:

1. `deployment.yaml` - Deployment configuration with resource limits and Datadog settings
2. `service.yaml` - Service configuration for accessing the application
3. `configmap.yaml` - ConfigMap for application properties

To deploy:

```bash
kubectl apply -f k8s/
```

## Application Structure

- `src/main/java/com/example/demo/DemoApplication.java` - Main application class
- `src/main/java/com/example/demo/controller/HelloController.java` - REST controller
- `src/main/resources/application.properties` - Application configuration
- `pom.xml` - Maven build configuration with Jib and buildpacks support
- `k8s/` - Kubernetes manifests

## Layering Support

The application is configured to use Spring Boot's layering feature:

1. Dependencies layer (changes less frequently)
2. Spring Boot loader layer
3. Snapshot dependencies layer
4. Application layer (changes most frequently)

This layering structure optimizes container image builds and updates.

## Customization

### Registry Configuration

Update the image name in the `pom.xml` file:

```xml
<to>
    <image>your-registry/spring-boot-k8s:latest</image>
</to>
```

### Datadog Configuration

Update Datadog environment variables in the `deployment.yaml` file:

```yaml
- name: DD_ENV
  value: "your-environment"
- name: DD_SERVICE
  value: "your-service-name"
```

## Troubleshooting

### Common Issues

1. **Image Pull Errors**: Ensure your Kubernetes cluster has access to the image registry
2. **Health Check Failures**: Verify the `/health` endpoint is accessible
3. **Datadog Connection Issues**: Check that the Datadog agent is properly configured in your cluster

### Logs

Access application logs:

```bash
kubectl logs -f deployment/spring-boot-k8s
```
