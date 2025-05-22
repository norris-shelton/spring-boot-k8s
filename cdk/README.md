# AWS CDK Deployment Guide for Spring Boot on EKS

This guide explains how to deploy the Spring Boot application to AWS EKS using the AWS CDK TypeScript project.

## Prerequisites

- [AWS CLI](https://aws.amazon.com/cli/) installed and configured
- [AWS CDK](https://docs.aws.amazon.com/cdk/latest/guide/getting_started.html) installed
- [Node.js](https://nodejs.org/) (v14 or later)
- [Docker](https://www.docker.com/) (for building and pushing container images to ECR)
- [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/) (for interacting with the EKS cluster)

## Setup

1. Navigate to the CDK project directory:
   ```bash
   cd cdk
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Configure Datadog API keys:
   
   Replace the placeholder values in `lib/spring-boot-eks-stack.ts`:
   ```typescript
   datadog: {
     apiKey: '${DATADOG_API_KEY}', // Replace with your Datadog API key
     appKey: '${DATADOG_APP_KEY}', // Replace with your Datadog APP key
     // ...
   }
   ```
   
   For production, use AWS Secrets Manager instead of hardcoding keys:
   ```typescript
   const datadogApiKey = secretsmanager.Secret.fromSecretNameV2(this, 'DatadogApiKey', 'datadog/api-key').secretValue.toString();
   ```

## Deployment Steps

1. Bootstrap your AWS environment (first time only):
   ```bash
   cdk bootstrap
   ```

2. Build the Spring Boot application using Jib:
   ```bash
   cd ..
   ./mvnw clean package jib:build -DskipTests -Djib.to.image=<AWS_ACCOUNT_ID>.dkr.ecr.<REGION>.amazonaws.com/spring-boot-k8s:latest
   ```

3. Deploy the CDK stack:
   ```bash
   cd cdk
   cdk deploy
   ```

4. After deployment, the CDK will output:
   - ECR Repository URI
   - EKS Cluster Name
   - IAM Role ARN for kubectl access

5. Configure kubectl to access your EKS cluster:
   ```bash
   aws eks update-kubeconfig --name spring-boot-k8s-cluster --region <REGION>
   ```

6. Verify the deployment:
   ```bash
   kubectl get pods
   kubectl get services
   ```

## Architecture

The CDK stack creates:

1. A VPC with public and private subnets
2. An ECR repository for your container images
3. An EKS cluster with t3.medium instances
4. The Datadog operator for monitoring
5. Kubernetes resources for your Spring Boot application:
   - Deployment with 2 replicas
   - LoadBalancer Service
   - ConfigMap for application properties

## Customization

- **Instance Type**: Modify `ec2.InstanceType.of()` in the stack
- **Cluster Size**: Change `defaultCapacity` value
- **Application Configuration**: Update the ConfigMap in the stack
- **Resource Limits**: Adjust the resource requests/limits in the deployment manifest

## Cleanup

To avoid incurring charges, delete the stack when not in use:

```bash
cdk destroy
```

## Troubleshooting

1. **Image Pull Errors**: Ensure your ECR repository exists and the image has been pushed
2. **Permission Issues**: Check IAM roles and policies
3. **Cluster Connection Issues**: Verify AWS CLI configuration and kubectl context

## Security Considerations

- The ECR repository has `removalPolicy: cdk.RemovalPolicy.DESTROY` which is not recommended for production
- Datadog API keys should be stored in AWS Secrets Manager for production use
- Consider implementing network policies for additional security
