# environments/prod/terraform.tfvars
# gitignored — never commit this file.
# Copy terraform.tfvars.example to terraform.tfvars and fill in real values.

project     = "myapp"
environment = "prod"
region      = "us-east-1" # replace with your provider's region identifier

network_cidr         = "10.2.0.0/16"
availability_zones   = ["zone-a", "zone-b", "zone-c"] # prod spans three AZs for HA
public_subnet_cidrs  = ["10.2.1.0/24", "10.2.2.0/24", "10.2.3.0/24"]
private_subnet_cidrs = ["10.2.10.0/24", "10.2.20.0/24", "10.2.30.0/24"]

kubernetes_version = "1.30"
node_instance_type = "standard-8vcpu-16gb" # replace with real large instance type
node_min_size      = 2
node_max_size      = 10
node_desired_size  = 3

db_instance_class    = "db.large" # replace with real production instance class
db_allocated_storage = 100
multi_az             = true  # HA standby replica required in prod
deletion_protection  = true  # prevent accidental deletion of the production database
skip_final_snapshot  = false # always retain a recovery snapshot before destroy in prod
