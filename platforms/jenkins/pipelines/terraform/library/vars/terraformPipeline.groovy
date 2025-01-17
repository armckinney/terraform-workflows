def call(Map pipelineParams) {
    pipeline {
        parameters {
            // configurable params
            string(name: 'awsAccountId', defaultValue: pipelineParams.awsAccountId, description: 'AWS Account ID to deploy from')
            string(name: 'configuration', defaultValue: pipelineParams.configuration, description: 'Terraform configuration to deploy')
            string(name: 'environment', defaultValue: pipelineParams.environment, description: 'Confirguration environment to deploy')
            string(name: 'pipelineToken', defaultValue: pipelineParams.pipelineToken, description: 'Unique token used to trigger the pipeline')

            // integrated params pushed by Bitbucket
            string(name: 'repositoryName', defaultValue: "", description: 'Name of repository, submitted within webhook request')
            string(name: 'branchName', defaultValue: "", description: 'Name of branch pushed, submitted within webhook request')
            string(name: 'commitHash', defaultValue: "", description: 'Commit hash of push, submitted within webhook request')
        }

        environment {
            AWS_ACCOUNT_ID = "${params.awsAccountId}"
            CONFIGURATION = "${params.configuration}"
            ENVIRONMENT = "${params.environment}"
            
            DOCKERFILE_PATH = '.devcontainer/Dockerfile'
            CONTAINER_REGISTRY_SERVER = 'docker.io'
            CONTAINER_REGISTRY_NAME = "${env.CONTAINER_REGISTRY_SERVER}/mysubdirectory"
            CONTAINER_REGISTRY_CREDENTIALS = credentials('my-container-registry-credentials')
            KUBERNETES_DEVCONTAINER_CONFIG = readFile('ci/library/resources/armckinney/platform/kubernetes-devcontainer-config.yaml'
                ).replace('$CONTAINER_IMAGE', "${env.CONTAINER_REGISTRY_NAME}/${env.REPOSITORY_NAME}:${env.BRANCH_NAME}")

            // exposing Bitbucket params
            REPOSITORY_NAME = "${params.repositoryName}"
            BRANCH_NAME = "${params.branchName}"
            COMMIT_HASH = "${params.commitHash}"
        }

        agent {
            label 'k8s-jenkins-slave'
        }
        stages {
            stage('container-build') {
                steps {
                    script {
                        containerBuild = load 'ci/stages/container-build.groovy'
                        containerBuild()
                    }
                }
            }
            
            stage('static-analysis') {
                parallel{
                    stage('terraform-validate') {
                        agent {
                            kubernetes {
                                yaml env.KUBERNETES_DEVCONTAINER_CONFIG
                                defaultContainer 'devcontainer'
                            }
                        }
                        steps {
                            script {
                                terraformValidate = load 'ci/stages/terraform-validate.groovy'
                                terraformValidate()
                            }
                        }
                    }
                    stage('terraform-format') {
                        agent {
                            kubernetes {
                                yaml env.KUBERNETES_DEVCONTAINER_CONFIG
                                defaultContainer 'devcontainer'
                            }
                        }
                        steps {
                            script {
                                terraformFormat = load 'ci/stages/terraform-format.groovy'
                                terraformFormat()
                            }
                        }
                    }
                    stage('terraform-lint') {
                        agent {
                            kubernetes {
                                yaml env.KUBERNETES_DEVCONTAINER_CONFIG
                                defaultContainer 'devcontainer'
                            }
                        }
                        steps {
                            script {
                                terraformLint = load 'ci/stages/terraform-lint.groovy'
                                terraformLint()
                            }
                        }
                    }
                    stage('terraform-docs') {
                        agent {
                            kubernetes {
                                yaml env.KUBERNETES_DEVCONTAINER_CONFIG
                                defaultContainer 'devcontainer'
                            }
                        }
                        steps {
                            script {
                                terraformDocs = load 'ci/stages/terraform-docs.groovy'
                                terraformDocs()
                            }
                        }
                    }
                    stage('terraform-security') {
                        agent {
                            kubernetes {
                                yaml env.KUBERNETES_DEVCONTAINER_CONFIG
                                defaultContainer 'devcontainer'
                            }
                        }
                        steps {
                            script {
                                terraformSecurity = load 'ci/stages/terraform-security.groovy'
                                terraformSecurity()
                            }
                        }
                    }
                }
            }

            stage('terraform-plan') {
                agent {
                    kubernetes {
                        yaml env.KUBERNETES_DEVCONTAINER_CONFIG
                        defaultContainer 'devcontainer'
                    }
                }
                steps {
                    script {
                        terraformPlan = load 'ci/stages/terraform-plan.groovy'
                        terraformPlan()
                    }
                }
            }

            stage('terraform-plan-approval') {
                agent {
                    label 'k8s-jenkins-slave'
                }
                input {
                    message 'Do you approve the terraform plan?'
                    ok 'Yes, I approve.'
                    submitterParameter 'APPROVAL_SUBMITTER'
                }
                options {
                    timeout(time: 1, unit: 'HOURS')
                    skipDefaultCheckout true
                }
                steps {
                    script {
                        terraformPlanApproval = load 'ci/stages/approval.groovy'
                        terraformPlanApproval()
                    }
                }
            }

            stage('terraform-action') {
                parallel {
                    stage('terraform-apply') {
                        agent {
                            kubernetes {
                                yaml env.KUBERNETES_DEVCONTAINER_CONFIG
                                defaultContainer 'devcontainer'
                            }
                        }
                        input {
                            message 'Run Terraform Apply?'
                            ok 'Yes, apply the terraform plan.'
                        }
                        options {
                            timeout(time: 15, unit: 'MINUTES')
                        }
                        steps {
                            script {
                                terraformApply = load 'ci/stages/terraform-apply.groovy'
                                terraformApply()
                            }
                        }
                    }
                    stage('terraform-show') {
                        agent {
                            kubernetes {
                                yaml env.KUBERNETES_DEVCONTAINER_CONFIG
                                defaultContainer 'devcontainer'
                            }
                        }
                        input {
                            message 'Run Terraform Show?'
                            ok 'Yes, show the current infrastructure.'
                        }
                        options {
                            timeout(time: 15, unit: 'MINUTES')
                        }
                        steps {
                            script {
                                terraformShow = load 'ci/stages/terraform-show.groovy'
                                terraformShow()
                            }
                        }
                    }
                    stage('terraform-destroy') {
                        agent {
                            kubernetes {
                                yaml env.KUBERNETES_DEVCONTAINER_CONFIG
                                defaultContainer 'devcontainer'
                            }
                        }
                        input {
                            message 'Run Terraform Destroy?'
                            ok 'Yes, destroy ALL infrastructure in the Terraform configuration.'
                        }
                        options {
                            timeout(time: 15, unit: 'MINUTES')
                        }
                        steps {
                            script {
                                terraformDestroy = load 'ci/stages/terraform-destroy.groovy'
                                terraformDestroy()
                            }
                        }
                    }
                }
            }
        }

        post {
            always {
                script {
                    notifyBitbucket commitSha1 params.commitHash
                }
            }
            cleanup {
                cleanWs()
            }
        }

        options {
            timestamps()
            timeout(time: 15, unit: 'MINUTES')
            buildDiscarder(logRotator(daysToKeepStr: '30', artifactDaysToKeepStr: '30'))
            disableConcurrentBuilds()
            skipDefaultCheckout false
        }

        triggers {
            GenericTrigger(
                causeString: 'Bitbucket Push Event',
                // REF: bitbucket push event payload - https://support.atlassian.com/bitbucket-cloud/docs/event-payloads/#Push
                genericVariables: [
                    [key: 'repositoryName', value: '$.repository.slug'],
                    [key: 'branchName', value: '$.push.changes[0].new.name'],
                    [key: 'commitHash', value: '$.push.changes[0].new.target.hash']
                ],
                genericRequestVariables: [
                    [key: 'baseUrl', regexpFilter: '']
                ],
                printContributedVariables: true,
                printPostContent: true,
                regexpFilterExpression: '',
                regexpFilterText: '',
                token: params.pipelineToken
            )
        }
    }
}