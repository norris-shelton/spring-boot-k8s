#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { SpringBootEksStack } from '../lib/spring-boot-eks-stack';

const app = new cdk.App();
new SpringBootEksStack(app, 'SpringBootEksStack', {
  env: { 
    account: process.env.CDK_DEFAULT_ACCOUNT, 
    region: process.env.CDK_DEFAULT_REGION 
  },
  description: 'Spring Boot 3 MVC application with AWS Corretto 21 and Datadog agent on EKS'
});
