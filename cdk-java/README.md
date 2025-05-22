# AWS CDK Java Project for Spring Boot EKS Deployment

This directory contains an AWS CDK application written in Java that deploys your Spring Boot application to Amazon EKS (Elastic Kubernetes Service).

## Project Structure

- `src/main/java/com/twinspires/cdk/SpringBootEksApp.java` - The CDK application entry point
- `src/main/java/com/twinspires/cdk/SpringBootEksStack.java` - The main CDK stack that defines the EKS cluster and resources
- `pom.xml` - Maven project configuration with AWS CDK dependencies
- `cdk.json` - CDK configuration file

## Prerequisites

- Java 21
- Maven
- AWS CLI configured with appropriate credentials
- AWS CDK CLI installed (`npm install -g aws-cdk`)

## Deployment Instructions

1. Build the project:
   ```
   mvn clean package
   ```

2. Bootstrap your AWS environment (if not already done):
   ```
   cdk bootstrap
   ```

3. Deploy the stack:
   ```
   cdk deploy
   ```

4. To build and push your Spring Boot application to the created ECR repository:
   ```
   # Get the ECR repository URI
   aws ecr describe-repositories --repository-names spring-boot-k8s --query 'repositories[0].repositoryUri' --output text
   
   # Build and push using Jib (update the ECR_REPOSITORY_URI with the value from above)
   mvn clean package jib:build -Djib.to.image=ECR_REPOSITORY_URI:latest
   ```

5. To destroy the stack when no longer needed:
   ```
   cdk destroy
   ```

## Features

- VPC with public and private subnets
- EKS cluster with managed node group
- ECR repository for container images
- Kubernetes namespace, deployment, and service for your Spring Boot application
- Datadog monitoring integration
- Health checks and resource limits

## Customization

You can customize the deployment by modifying the `SpringBootEksStack.java` file:

- Change instance types or node group size
- Adjust resource limits for the Spring Boot containers
- Modify the Kubernetes deployment and service configurations
- Add additional AWS resources as needed

## Security Considerations

- The EKS cluster is configured with both public and private endpoint access
- IAM roles are set up for proper access control
- The ECR repository is configured with appropriate permissions
