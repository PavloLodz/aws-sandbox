# environments/staging/terraform.tfvars
# gitignored — never commit this file.
# Copy terraform.tfvars.example to terraform.tfvars and fill in real values.

project     = "myapp"
environment = "staging"
region      = "us-east-1" # replace with your provider's region identifier

network_cidr         = "10.1.0.0/16"
availability_zones   = ["zone-a", "zone-b"] # replace with real zone names for chosen provider
public_subnet_cidrs  = ["10.1.1.0/24", "10.1.2.0/24"]
private_subnet_cidrs = ["10.1.10.0/24", "10.1.20.0/24"]

kubernetes_version = "1.30"
node_instance_type = "standard-4vcpu-8gb" # replace with real medium instance type
node_min_size      = 1
node_max_size      = 5
node_desired_size  = 2

db_instance_class    = "db.medium" # replace with real instance class
db_allocated_storage = 20
multi_az             = false
deletion_protection  = false
skip_final_snapshot  = true
