package com.twinspires.cdk;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.eks.Cluster;
import software.amazon.awscdk.services.eks.KubernetesManifest;
import software.amazon.awscdk.services.eks.KubernetesVersion;
import software.amazon.awscdk.services.eks.NodegroupOptions;
import software.amazon.awscdk.services.eks.CapacityType;
import software.amazon.awscdk.services.eks.EndpointAccess;
import software.amazon.awscdk.services.iam.AccountRootPrincipal;
import software.constructs.Construct;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpringBootEksStack extends Stack {
    public SpringBootEksStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Create a VPC for our cluster
        Vpc vpc = Vpc.Builder.create(this, "SpringBootVpc")
                .maxAzs(2)
                .subnetConfiguration(Arrays.asList(
                        SubnetConfiguration.builder()
                                .name("public")
                                .subnetType(SubnetType.PUBLIC)
                                .cidrMask(24)
                                .build(),
                        SubnetConfiguration.builder()
                                .name("private")
                                .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                                .cidrMask(24)
                                .build()
                ))
                .build();

        // Create an ECR repository for our container images
        Repository ecrRepository = Repository.Builder.create(this, "SpringBootRepository")
                .repositoryName("spring-boot-k8s")
                .removalPolicy(software.amazon.awscdk.RemovalPolicy.DESTROY)
                .build();

        // Create an EKS cluster
        Cluster cluster = Cluster.Builder.create(this, "SpringBootCluster")
                .version(KubernetesVersion.V1_27)
                .vpc(vpc)
                .defaultCapacity(0) // We will define the nodegroup separately
                .endpointAccess(EndpointAccess.PUBLIC_AND_PRIVATE)
                .clusterName("spring-boot-eks")
                .build();

        // Add IAM user access to the cluster
        cluster.awsAuth().addMastersRole(new AccountRootPrincipal());

        // Add a managed nodegroup
        cluster.addNodegroupCapacity("SpringBootNodeGroup", NodegroupOptions.builder()
                .instanceTypes(Arrays.asList(
                        new software.amazon.awscdk.services.ec2.InstanceType("t3.medium")
                ))
                .minSize(2)
                .maxSize(4)
                .desiredSize(2)
                .capacityType(CapacityType.ON_DEMAND)
                .build());

        // Define Kubernetes namespace
        Map<String, Object> namespaceManifest = new HashMap<>();
        namespaceManifest.put("apiVersion", "v1");
        namespaceManifest.put("kind", "Namespace");
        Map<String, Object> namespaceMetadata = new HashMap<>();
        namespaceMetadata.put("name", "spring-boot");
        namespaceManifest.put("metadata", namespaceMetadata);

        KubernetesManifest namespace = KubernetesManifest.Builder.create(this, "SpringBootNamespace")
                .cluster(cluster)
                .manifest(Collections.singletonList(namespaceManifest))
                .build();

        // Define Kubernetes deployment
        Map<String, Object> deploymentManifest = createDeploymentManifest();
        KubernetesManifest deployment = KubernetesManifest.Builder.create(this, "SpringBootDeployment")
                .cluster(cluster)
                .manifest(Collections.singletonList(deploymentManifest))
                .build();
        deployment.node().addDependency(namespace);

        // Define Kubernetes service
        Map<String, Object> serviceManifest = createServiceManifest();
        KubernetesManifest service = KubernetesManifest.Builder.create(this, "SpringBootService")
                .cluster(cluster)
                .manifest(Collections.singletonList(serviceManifest))
                .build();
        service.node().addDependency(deployment);
    }

    private Map<String, Object> createDeploymentManifest() {
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("apiVersion", "apps/v1");
        manifest.put("kind", "Deployment");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "spring-boot-k8s");
        metadata.put("namespace", "spring-boot");
        manifest.put("metadata", metadata);

        Map<String, Object> spec = new HashMap<>();
        spec.put("replicas", 2);

        Map<String, Object> selector = new HashMap<>();
        Map<String, Object> matchLabels = new HashMap<>();
        matchLabels.put("app", "spring-boot-k8s");
        selector.put("matchLabels", matchLabels);
        spec.put("selector", selector);

        Map<String, Object> template = new HashMap<>();
        Map<String, Object> templateMetadata = new HashMap<>();
        Map<String, Object> labels = new HashMap<>();
        labels.put("app", "spring-boot-k8s");
        templateMetadata.put("labels", labels);
        template.put("metadata", templateMetadata);

        Map<String, Object> templateSpec = new HashMap<>();
        List<Map<String, Object>> containers = Arrays.asList(createContainerSpec());
        templateSpec.put("containers", containers);
        template.put("spec", templateSpec);
        spec.put("template", template);
        manifest.put("spec", spec);

        return manifest;
    }

    private Map<String, Object> createContainerSpec() {
        Map<String, Object> container = new HashMap<>();
        container.put("name", "spring-boot-k8s");
        container.put("image", "${ECR_REPOSITORY_URI}:latest");

        List<Map<String, Object>> ports = Arrays.asList(
                Collections.singletonMap("containerPort", 8080)
        );
        container.put("ports", ports);

        Map<String, Object> resources = new HashMap<>();
        Map<String, Object> limits = new HashMap<>();
        limits.put("cpu", "1000m");
        limits.put("memory", "1024Mi");
        Map<String, Object> requests = new HashMap<>();
        requests.put("cpu", "500m");
        requests.put("memory", "512Mi");
        resources.put("limits", limits);
        resources.put("requests", requests);
        container.put("resources", resources);

        List<Map<String, Object>> env = Arrays.asList(
                createEnvVar("SPRING_PROFILES_ACTIVE", "prod"),
                createEnvVar("DD_APM_ENABLED", "true"),
                createEnvVar("DD_LOGS_ENABLED", "true"),
                createEnvVar("DD_PROCESS_AGENT_ENABLED", "true")
        );
        container.put("env", env);

        Map<String, Object> livenessProbe = new HashMap<>();
        Map<String, Object> httpGet = new HashMap<>();
        httpGet.put("path", "/actuator/health");
        httpGet.put("port", 8080);
        livenessProbe.put("httpGet", httpGet);
        livenessProbe.put("initialDelaySeconds", 60);
        livenessProbe.put("periodSeconds", 10);
        container.put("livenessProbe", livenessProbe);

        Map<String, Object> readinessProbe = new HashMap<>();
        Map<String, Object> readinessHttpGet = new HashMap<>();
        readinessHttpGet.put("path", "/actuator/health");
        readinessHttpGet.put("port", 8080);
        readinessProbe.put("httpGet", readinessHttpGet);
        readinessProbe.put("initialDelaySeconds", 30);
        readinessProbe.put("periodSeconds", 5);
        container.put("readinessProbe", readinessProbe);

        return container;
    }

    private Map<String, Object> createEnvVar(String name, String value) {
        Map<String, Object> env = new HashMap<>();
        env.put("name", name);
        env.put("value", value);
        return env;
    }

    private Map<String, Object> createServiceManifest() {
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("apiVersion", "v1");
        manifest.put("kind", "Service");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", "spring-boot-k8s");
        metadata.put("namespace", "spring-boot");
        manifest.put("metadata", metadata);

        Map<String, Object> spec = new HashMap<>();
        spec.put("type", "LoadBalancer");

        Map<String, Object> selector = new HashMap<>();
        selector.put("app", "spring-boot-k8s");
        spec.put("selector", selector);

        List<Map<String, Object>> ports = Arrays.asList(
                createServicePort("http", 80, 8080)
        );
        spec.put("ports", ports);
        manifest.put("spec", spec);

        return manifest;
    }

    private Map<String, Object> createServicePort(String name, int port, int targetPort) {
        Map<String, Object> servicePort = new HashMap<>();
        servicePort.put("name", name);
        servicePort.put("port", port);
        servicePort.put("targetPort", targetPort);
        return servicePort;
    }
}
