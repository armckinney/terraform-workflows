# GitHub Workflows

## Reusable Workflows

- [rwf-auto-pull-request](../../.github/workflows/rwf-auto-pull-request.yaml): create a pull request when an issue's assignie is set & delete Pr/Branch on issue closed
- [rwf-terraform-deployment-ci](.github/workflows/rwf-terraform-deployment-ci.yaml): run a terraform deployment on a structured configuration inside of a container, this includes static analysis, planning, and applying
- [rwf-terraform-matrix-deployment-ci](.github/workflows/rwf-terraform-matrix-deployment-ci.yaml): run a terraform deployment on a structured configuration - across a matrix of environments - inside of a container, this includes linting, planning, and applying
- [rwf-container-build-and-push](.github/workflows/rwf-container-build-and-push.yaml): builds and pushes a container image from a repository dockerfile, useful for devcontainers
- [rwf-terraform-static-analysis](.github/workflows/rwf-terraform-static-analysis.yaml): run terraform static analysis on a structured configuration inside of a container, this includes lint, validate, docs, security, and format
- [rwf-terraform-apply](.github/workflows/rwf-terraform-apply.yaml): run terraform apply on a structured configuration inside of a container
- [rwf-terraform-destroy](.github/workflows/rwf-terraform-destroy.yaml): run terraform destroy on a structured configuration inside of a container
- [rwf-terraform-plan](.github/workflows/rwf-terraform-plan.yaml): run terraform plan on a structured configuration inside of a container
- [rwf-terraform-restate](.github/workflows/rwf-terraform-restate.yaml): run terraform import on a structured configuration inside of a container, this will recreate the statefile, useful when corrupted
- [rwf-terraform-show](.github/workflows/rwf-terraform-show.yaml): run terraform show on a structured configuration inside of a container


## Usage

Usage of the CICD logic defined in this repository can be done via examples in which this repository uses for it's own CICD.

#### Examples

- [wf-terraform-ci-{configuration}-{environment}](.github/workflows/wf-terraform-ci-example-dev.yaml): run terraform ci pipeline on a single configuration/environment, useful for development branch deployments
- [wf-terraform-ci-release](.github/workflows/wf-terraform-ci-release.yaml): run terraform ci pipeline on a test environment and then release changes to all environments in a matrix implementation on merge to main
- [wf-terraform-apply](.github/workflows/wf-terraform-apply.yaml): run adhoc terraform apply on an input configuration/environment on the main branch
- [wf-terraform-destroy](.github/workflows/wf-terraform-destroy.yaml): run adhoc terraform destroy on an input configuration/environment on the main branch
- [wf-terraform-show](.github/workflows/wf-terraform-show.yaml): run adhoc terraform show on an input configuration/environment on the main branch
- [wf-terraform-restate](.github/workflows/wf-terraform-restate.yaml): run adhoc terraform restate on an input configuration/environment on the main branch, this will recreate the statefile, useful if corrupt

##  Architecture

GitHub allows DevOps automation via GitHub Actions / Workflows. There are several components that are available within the platform for automation and organization - these include, Actions, Composite Actions, Reusable Workflows, etc.

The architecture of this implementation is as follows:

- `Workflows` are automations defined within a repository and implement reusable workflows, these are denoted with a prefix `wf-<name_of_workflow>`
- `Reusable Workflows` are modules of automation that can comprise of 1 or more atomic actions, Reusable Workflows can implement other Reusable Workflows to build on top of their modulization, these are denoted with a prefix `rwf-<name_of_workflow>`

#### A Note on Actions

It was decided to not use GitHub actions to modulize functionality because of the following reasons:

- Published Actions: Published Actions have overhead and are not truly intended to be consumed outside of the scope of this repository. 
    - It is possible to implement functionality this way if it is intended to be used elsewhere by other actors on GitHub.

- Composite Actions: Composite Actions allow for grouping of automation steps into a logical unit. It was decided not to utilize these since Jobs do not share file systems by default, meaning that for Terraform Workflows which intend to persist data across functionality (i.e. terraform init needed before terraform plan) that a Cache / Artifact upload/download would be nessecary. Because of this the mapping of Composite Actions to Reusable Workflows was 1:1 - this means that the Reusable Workflow would be a passthrough interface to the Composite Action logic, and that it only introduces verbosity and complexity. 
    - It it possible to implement functionality using composite actions, but only if the functionality is truly intended to be shared across Reusable Workflows in a non-1:1 manner. (i.e. see terraform-init in [rwf-terraform-matrix-deployment-ci](.github/workflows/rwf-terraform-matrix-deployment-ci.yaml) and in [rwf-terraform-plan](.github/workflows/rwf-terraform-plan.yaml))