# infrastructure/terraform

AWS infrastructure provisioning with remote state.

## Modules

| Module | Provisions |
|--------|-----------|
| `vpc/` | VPC, subnets, route tables, NAT gateway |
| `eks/` | EKS cluster, managed node group, IAM roles |
| `rds/` | PostgreSQL RDS instance in private subnet |
| `ecr/` | Elastic Container Registry repository |

## Remote state

State is stored in S3 with DynamoDB locking. See `backend.tf`.
Never use local state for shared infrastructure.

## Usage

```bash
# Initialise (run once per environment)
make tf-init ENV=dev

# Preview changes
make tf-plan ENV=dev

# Apply
make tf-apply ENV=dev

# Destroy (removes all resources — watch cloud costs)
make tf-destroy ENV=dev
```

## Secrets

Never commit secrets. Use environment variables or AWS Secrets Manager.
`terraform.tfvars` files are gitignored — see `terraform.tfvars.example`.
