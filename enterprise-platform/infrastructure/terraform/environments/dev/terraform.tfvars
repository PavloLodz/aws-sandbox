# environments/dev/terraform.tfvars
# gitignored — never commit this file.
# Copy terraform.tfvars.example to terraform.tfvars and fill in real values.

project     = "myapp"
environment = "dev"
region      = "us-east-1" # replace with your provider's region identifier

network_cidr         = "10.0.0.0/16"
availability_zones   = ["zone-a", "zone-b"] # replace with real zone names for chosen provider
public_subnet_cidrs  = ["10.0.1.0/24", "10.0.2.0/24"]
private_subnet_cidrs = ["10.0.10.0/24", "10.0.20.0/24"]

kubernetes_version = "1.30"
node_instance_type = "standard-2vcpu-4gb" # replace with real instance type (e.g. t3.medium on AWS)
node_min_size      = 1
node_max_size      = 3
node_desired_size  = 1

db_instance_class    = "db.small" # replace with real instance class (e.g. db.t3.micro on AWS)
db_allocated_storage = 20
multi_az             = false
deletion_protection  = false
skip_final_snapshot  = true
