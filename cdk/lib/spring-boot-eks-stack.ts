import * as cdk from 'aws-cdk-lib';
import { Construct } from 'constructs';
import * as eks from 'aws-cdk-lib/aws-eks';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as iam from 'aws-cdk-lib/aws-iam';
import * as ecr from 'aws-cdk-lib/aws-ecr';

export class SpringBootEksStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props?: cdk.StackProps) {
    super(scope, id, props);

    // Create a VPC for our cluster
    const vpc = new ec2.Vpc(this, 'SpringBootEksVpc', {
      maxAzs: 3,
      natGateways: 1, // Use 1 NAT Gateway to save costs
    });

    // Create an ECR repository for our Spring Boot application
    const repository = new ecr.Repository(this, 'SpringBootRepository', {
      repositoryName: 'spring-boot-k8s',
      removalPolicy: cdk.RemovalPolicy.DESTROY, // NOT recommended for production
      imageScanOnPush: true,
    });

    // Create an EKS cluster
    const cluster = new eks.Cluster(this, 'SpringBootCluster', {
      version: eks.KubernetesVersion.V1_27,
      vpc,
      defaultCapacity: 2, // Default capacity of 2 nodes
      defaultCapacityInstance: ec2.InstanceType.of(
        ec2.InstanceClass.T3,
        ec2.InstanceSize.MEDIUM
      ),
      clusterName: 'spring-boot-k8s-cluster',
    });

    // Add Datadog Operator to the cluster
    cluster.addHelmChart('DatadogOperator', {
      chart: 'datadog-operator',
      repository: 'https://helm.datadoghq.com',
      namespace: 'datadog',
      createNamespace: true,
      values: {
        // These values would need to be customized for your Datadog account
        datadog: {
          apiKey: '${DATADOG_API_KEY}', // Replace with your Datadog API key or use AWS Secrets Manager
          appKey: '${DATADOG_APP_KEY}', // Replace with your Datadog APP key or use AWS Secrets Manager
          site: 'datadoghq.com',
          apm: {
            enabled: true,
          },
          logs: {
            enabled: true,
            containerCollectAll: true,
          },
          processAgent: {
            enabled: true,
          },
          dogstatsd: {
            useHostPort: true,
          },
        },
      },
    });

    // Deploy our Spring Boot application to the cluster
    const appLabel = { app: 'spring-boot-k8s' };
    
    const deployment = {
      apiVersion: 'apps/v1',
      kind: 'Deployment',
      metadata: {
        name: 'spring-boot-k8s',
        labels: appLabel,
      },
      spec: {
        replicas: 2,
        selector: { matchLabels: appLabel },
        strategy: {
          type: 'RollingUpdate',
          rollingUpdate: {
            maxSurge: 1,
            maxUnavailable: 0,
          },
        },
        template: {
          metadata: {
            labels: appLabel,
            annotations: {
              'ad.datadoghq.com/java.logs': '[{"source":"java","service":"spring-boot-k8s"}]',
            },
          },
          spec: {
            containers: [{
              name: 'spring-boot-k8s',
              image: `${repository.repositoryUri}:latest`,
              ports: [{ containerPort: 8080 }],
              resources: {
                requests: {
                  memory: '512Mi',
                  cpu: '200m',
                },
                limits: {
                  memory: '1Gi',
                  cpu: '500m',
                },
              },
              readinessProbe: {
                httpGet: {
                  path: '/health',
                  port: 8080,
                },
                initialDelaySeconds: 30,
                periodSeconds: 10,
              },
              livenessProbe: {
                httpGet: {
                  path: '/health',
                  port: 8080,
                },
                initialDelaySeconds: 60,
                periodSeconds: 20,
              },
              env: [
                {
                  name: 'JAVA_OPTS',
                  value: '-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0',
                },
                {
                  name: 'DD_AGENT_HOST',
                  valueFrom: {
                    fieldRef: {
                      fieldPath: 'status.hostIP',
                    },
                  },
                },
                {
                  name: 'DD_ENV',
                  value: 'production',
                },
                {
                  name: 'DD_SERVICE',
                  value: 'spring-boot-k8s',
                },
                {
                  name: 'DD_VERSION',
                  value: '1.0.0',
                },
                {
                  name: 'DD_LOGS_INJECTION',
                  value: 'true',
                },
                {
                  name: 'DD_TRACE_SAMPLE_RATE',
                  value: '1',
                },
                {
                  name: 'DD_PROFILING_ENABLED',
                  value: 'true',
                },
              ],
              volumeMounts: [{
                name: 'config-volume',
                mountPath: '/app/config',
              }],
            }],
            volumes: [{
              name: 'config-volume',
              configMap: {
                name: 'spring-boot-k8s-config',
              },
            }],
          },
        },
      },
    };

    const service = {
      apiVersion: 'v1',
      kind: 'Service',
      metadata: {
        name: 'spring-boot-k8s',
        labels: appLabel,
      },
      spec: {
        type: 'LoadBalancer', // Using LoadBalancer to expose the service externally
        ports: [{
          port: 80,
          targetPort: 8080,
          protocol: 'TCP',
          name: 'http',
        }],
        selector: appLabel,
      },
    };

    const configMap = {
      apiVersion: 'v1',
      kind: 'ConfigMap',
      metadata: {
        name: 'spring-boot-k8s-config',
      },
      data: {
        'application.properties': `
spring.application.name=spring-boot-k8s
server.port=8080

# Actuator configuration
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=always

# Datadog configuration
dd.service.name=spring-boot-k8s
dd.env=production
dd.version=1.0.0
        `,
      },
    };

    // Apply Kubernetes manifests to the cluster
    cluster.addManifest('SpringBootApp', deployment, service, configMap);

    // Output the ECR repository URI
    new cdk.CfnOutput(this, 'RepositoryUri', {
      value: repository.repositoryUri,
      description: 'ECR Repository URI for the Spring Boot application',
    });

    // Output the EKS cluster name
    new cdk.CfnOutput(this, 'ClusterName', {
      value: cluster.clusterName,
      description: 'EKS Cluster Name',
    });

    // Output the kubectl role ARN
    new cdk.CfnOutput(this, 'KubectlRoleArn', {
      value: cluster.kubectlRole?.roleArn || '',
      description: 'IAM Role ARN for kubectl access',
    });
  }
}
