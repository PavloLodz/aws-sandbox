# Terraform — Topic 4

How to initialise, plan, apply, verify, and destroy the `myapp` infrastructure
using the four reusable Terraform modules across dev, staging, and prod
environments.

All commands assume you are at the repository root unless noted otherwise.
The `make` targets are wrappers around `terraform -chdir=...` — the raw
equivalents are shown alongside each target for transparency.

---

## Prerequisites

Confirm all of the following before running any Terraform command:

```bash
# Confirm Terraform >= 1.8 is installed
terraform version

# Confirm the provider CLI is installed and authenticated
# AWS example:
aws sts get-caller-identity

# GCP example:
gcloud auth application-default print-access-token

# Azure example:
az account show

# Confirm tfsec is installed for local security scanning
tfsec --version
# Install if missing: https://aquasecurity.github.io/tfsec/latest/guides/installation/
```

Terraform requires provider credentials to be available in the shell
environment before `terraform init` and every subsequent command. The exact
mechanism (environment variables, CLI profile, workload identity) depends on
the chosen provider — consult the provider documentation for setup.

---

## Directory Structure

```
infrastructure/terraform/
├── modules/                        # Reusable, provider-agnostic logic
│   ├── vpc/                        #   Network, public/private subnets, NAT gateways
│   ├── eks/                        #   Kubernetes cluster, node group, workload identity
│   ├── rds/                        #   Managed PostgreSQL, random_password, secrets store
│   └── ecr/                        #   Container image registry with lifecycle policy
└── environments/                   # Module composition + variable values per target
    ├── dev/
    │   ├── main.tf                 #   Wires all four modules together
    │   ├── variables.tf            #   Input variable declarations
    │   ├── backend.tf              #   State backend configuration
    │   ├── terraform.tfvars        #   Per-environment values (gitignored)
    │   └── terraform.tfvars.example  # Copy-and-fill template (committed)
    ├── staging/
    │   └── ... (same structure)
    └── prod/
        └── ... (same structure)

ci/github-actions/
└── terraform-ci.yml                # fmt-check, validate, tfsec for all three envs

docs/
└── terraform.md                    # This file
```

**`modules/`** contains provider-agnostic HCL logic. Modules define *what*
resources exist and *how* they are wired together, but never hardcode
environment-specific values such as instance sizes, CIDR blocks, or replica
counts. Modules are the reusable building blocks.

**`environments/`** contains module composition roots. Each environment
directory calls the shared modules and supplies environment-specific variable
values via `terraform.tfvars`. The `main.tf` files are structurally identical
across dev, staging, and prod — only the variable values in `terraform.tfvars`
differ.

---

## Remote State Bootstrap

> **Learning phase:** `backend.tf` is configured for a local state file. No
> bootstrap is needed. Skip this section and proceed to [Init](#init).
> When you are ready to move to a shared remote backend, return here.

Remote state requires an object-storage bucket and (for AWS) a DynamoDB lock
table to exist *before* the first `terraform init`. These resources cannot be
managed by the same Terraform code because a circular dependency would result —
you need state to manage state.

Create these resources once using the provider CLI, then configure `backend.tf`
with the bucket name and lock table name:

```bash
# AWS example — create S3 bucket and DynamoDB lock table
aws s3api create-bucket \
  --bucket myapp-terraform-state \
  --region us-east-1

aws dynamodb create-table \
  --table-name myapp-terraform-locks \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region us-east-1

# GCP example — create GCS bucket
gcloud storage buckets create gs://myapp-terraform-state \
  --location=us-central1 \
  --uniform-bucket-level-access
```

After creating the bucket, update `backend.tf` in each environment directory
to reference it, then run `terraform init` to migrate any existing local state.

---

## Init

Initialise the working directory: downloads provider plugins, sets up the
backend, and resolves module sources. Run once after cloning and again after
any change to `required_providers`, `backend.tf`, or module source paths.

```bash
# Initialise dev
make tf-init ENV=dev
# equivalent to:
terraform -chdir=infrastructure/terraform/environments/dev init

# Initialise staging
make tf-init ENV=staging

# Initialise prod
make tf-init ENV=prod

# Re-initialise after adding a new provider or module source
make tf-init ENV=dev EXTRA_ARGS="-upgrade"
# equivalent to:
terraform -chdir=infrastructure/terraform/environments/dev init -upgrade
```

A successful init ends with:

```
Terraform has been successfully initialized!
```

---

## Plan

Preview all changes Terraform would make without applying them. Always run
`plan` before `apply` — there are no surprises at apply time if you review the
plan first.

```bash
# Preview changes for dev
make tf-plan ENV=dev
# equivalent to:
terraform -chdir=infrastructure/terraform/environments/dev plan

# Save plan to file — apply executes exactly this plan with no re-evaluation
terraform -chdir=infrastructure/terraform/environments/dev plan -out=dev.tfplan
```

The plan output shows `+` (create), `~` (update in place), `-/+` (destroy and
recreate), and `-` (destroy). Review all `-` and `-/+` lines carefully before
proceeding to apply.

---

## Apply

Apply the planned changes. Terraform prompts for `yes` confirmation before
making any changes to real infrastructure.

```bash
# Apply changes for dev (interactive confirmation prompt)
make tf-apply ENV=dev
# equivalent to:
terraform -chdir=infrastructure/terraform/environments/dev apply

# Apply a saved plan file — no re-evaluation, no confirmation prompt
terraform -chdir=infrastructure/terraform/environments/dev apply dev.tfplan
```

Apply a saved plan file when you need the exact changes reviewed in the plan
step to be applied without any risk of the plan changing between review and
apply (for example, in CI/CD pipelines or after a review gate).

---

## Verify Outputs

After a successful apply, inspect the outputs to confirm the environment is
ready and to retrieve the endpoints needed by other services.

```bash
# Show all outputs for dev
terraform -chdir=infrastructure/terraform/environments/dev output

# Show a single output
terraform -chdir=infrastructure/terraform/environments/dev output cluster_endpoint
```

Expected outputs after a complete apply:

| Output | Description |
|---|---|
| `cluster_endpoint` | API server endpoint of the Kubernetes cluster |
| `db_endpoint` | Hostname of the PostgreSQL instance |
| `db_secret_arn` | ARN of the secrets-manager secret holding DB credentials JSON |
| `registry_url` | Full URL of the container image registry |

To retrieve the database credentials stored in the secrets service at runtime:

```bash
# Provider-specific — replace with the real CLI command for your provider
# AWS example:
aws secretsmanager get-secret-value \
  --secret-id $(terraform -chdir=infrastructure/terraform/environments/dev output -raw db_secret_arn) \
  | jq -r .SecretString | jq .
```

The secret contains the full credentials JSON including the auto-generated
password. Never print or log `db_secret_arn` contents in CI.

---

## Destroy

Tear down all resources managed by Terraform in an environment. Irreversible —
confirm the environment is no longer needed before proceeding.

```bash
# Destroy all resources for dev (interactive confirmation prompt)
make tf-destroy ENV=dev
# equivalent to:
terraform -chdir=infrastructure/terraform/environments/dev destroy
```

Before running destroy, check:

- `deletion_protection` must be `false` — it is `false` for dev and staging;
  for prod you must set it to `false` in `terraform.tfvars` and apply first.
- Registry images may need manual deletion before the repository resource can
  be destroyed. Empty the registry first:
  ```bash
  # AWS ECR example — list and delete all images before destroy
  aws ecr list-images --repository-name myapp-dev \
    --query 'imageIds[*]' --output json | \
    xargs -I{} aws ecr batch-delete-image --repository-name myapp-dev --image-ids {}
  ```
- PersistentVolumes, volume snapshots, and automated database snapshots are
  often not managed by Terraform and will survive `destroy`. Clean these up
  manually via the provider console or CLI after the destroy completes.

---

## Importing Existing Resources

Use `terraform import` to bring pre-existing infrastructure under Terraform
management without recreating it.

**Three-step import pattern:**

1. Write the resource block in the appropriate `.tf` file, matching the
   existing resource's configuration as closely as possible.

2. Run `terraform import` to write the resource into state:

   ```bash
   terraform -chdir=infrastructure/terraform/environments/dev \
     import aws_db_instance.main myapp-dev-postgres
   ```

3. Run `terraform plan` to verify no unintended changes will be applied:

   ```bash
   terraform -chdir=infrastructure/terraform/environments/dev plan
   ```
   The plan should show no changes (`No changes. Your infrastructure matches the configuration.`).
   If it shows changes, update the resource block to match the live resource
   before running apply.

> **Important:** `terraform import` only writes state — it does not change the
> real resource and it does not validate whether your `.tf` configuration
> matches the live resource. Always follow with `terraform plan` to detect any
> configuration drift before applying.

---

## Handling State Drift

State drift occurs when live infrastructure diverges from the Terraform state
file — typically caused by manual changes in the provider console, automated
scripts outside Terraform, or resources modified by other tooling.

```bash
# Detect drift — refresh state against live infrastructure without applying changes
terraform -chdir=infrastructure/terraform/environments/dev plan -refresh-only

# Reconcile drift — update state to match live infrastructure
# (use when you want Terraform to accept the manual change)
terraform -chdir=infrastructure/terraform/environments/dev apply -refresh-only

# Remove a resource from state without destroying it
# (use when a resource was deleted outside Terraform and you want to stop managing it)
terraform -chdir=infrastructure/terraform/environments/dev state rm aws_db_instance.main
# Then re-import if you want to bring it back under management.
```

> **Note:** `terraform refresh` was deprecated in Terraform 1.x. Use
> `plan -refresh-only` to inspect drift and `apply -refresh-only` to reconcile
> it. These commands are explicit about their intent and safer than the old
> `refresh` subcommand.

The preferred resolution for drift is to either:
- **Reconcile with `apply -refresh-only`** — accept the manual change into
  state, then let Terraform manage it going forward.
- **Revert the manual change** — run `terraform apply` to restore the resource
  to its declared configuration.

Avoid leaving drift unresolved — it causes plan output to show phantom changes
and makes it harder to reason about what Terraform will do next.

---

## Secret Hygiene Rules

```
✗ Never put passwords or tokens in terraform.tfvars
✗ Never pass secrets via -var flags in CI — they appear in shell history and CI logs
✗ Never output the database password — output the secret ARN and read at runtime

✓ Use random_password to generate credentials at apply time
✓ Store generated credentials in the secrets management service immediately
✓ Enable state encryption (encrypt = true) — state files contain generated password material
✓ Rotate secrets by tainting random_password.<name> and running apply —
  the new password is automatically pushed to the secrets service and the database
```

State files contain every value of every managed resource, including generated
passwords from `random_password`. Ensure your state backend has encryption at
rest enabled before storing state for any environment that handles real data.

---

## Terraform Commands Reference

| Command | Purpose |
|---|---|
| `terraform version` | Confirm Terraform ≥ 1.8 is installed |
| `make tf-init ENV=dev` | Initialise providers and backend for dev |
| `make tf-init ENV=staging` | Initialise providers and backend for staging |
| `make tf-init ENV=prod` | Initialise providers and backend for prod |
| `make tf-init ENV=dev EXTRA_ARGS="-upgrade"` | Re-initialise after adding a new provider or module source |
| `make tf-plan ENV=dev` | Preview changes for dev (no apply) |
| `make tf-apply ENV=dev` | Apply changes for dev |
| `make tf-destroy ENV=dev` | Destroy all resources for dev |
| `terraform -chdir=infrastructure/terraform/environments/dev output` | Show outputs from last apply |
| `terraform fmt -recursive infrastructure/terraform/` | Format all .tf files in place |
| `terraform fmt -check -recursive infrastructure/terraform/` | Check formatting without changing files (used in CI) |
| `terraform -chdir=infrastructure/terraform/environments/dev validate` | Validate HCL syntax and module references |
| `terraform -chdir=infrastructure/terraform/environments/dev plan -out=dev.tfplan` | Save plan to file for gated apply |
| `terraform -chdir=infrastructure/terraform/environments/dev apply dev.tfplan` | Apply a saved plan exactly (no re-evaluation, no prompt) |
| `terraform -chdir=infrastructure/terraform/environments/dev plan -refresh-only` | Detect state drift without applying changes |
| `terraform -chdir=infrastructure/terraform/environments/dev apply -refresh-only` | Reconcile state drift by updating state to match live infrastructure |
| `terraform -chdir=infrastructure/terraform/environments/dev import <resource> <id>` | Adopt an existing resource into Terraform state |
| `terraform -chdir=infrastructure/terraform/environments/dev state list` | List all resources tracked in state |
| `terraform -chdir=infrastructure/terraform/environments/dev state show <resource>` | Inspect a specific resource in state |
| `terraform -chdir=infrastructure/terraform/environments/dev state rm <resource>` | Remove resource from state without destroying it |
| `tfsec infrastructure/terraform/` | Run tfsec security scan locally |
