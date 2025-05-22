package com.twinspires.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class SpringBootEksApp {
    public static void main(final String[] args) {
        App app = new App();

        Environment env = Environment.builder()
                .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
                .region(System.getenv("CDK_DEFAULT_REGION"))
                .build();

        StackProps stackProps = StackProps.builder()
                .env(env)
                .description("Spring Boot application deployment on EKS")
                .build();

        new SpringBootEksStack(app, "SpringBootEksStack", stackProps);

        app.synth();
    }
}
