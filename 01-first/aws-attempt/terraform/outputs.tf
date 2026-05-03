# These outputs are used directly by Ansible after `terraform apply`:
#   terraform output -raw ec2_public_ip      → Ansible inventory host
#   terraform output -raw rds_endpoint       → RDS_HOSTNAME env var
#   terraform output -raw s3_bucket_name     → S3_BUCKET env var

output "ec2_public_ip" {
  description = "Public IP of the EC2 application server — use as Ansible inventory host"
  value       = module.ec2.public_ip
}

output "ec2_instance_id" {
  description = "EC2 instance ID"
  value       = module.ec2.instance_id
}

output "rds_endpoint" {
  description = "RDS hostname (without port) — maps to RDS_HOSTNAME in application-aws.properties"
  value       = module.rds.endpoint
}

output "rds_port" {
  description = "RDS port — maps to RDS_PORT"
  value       = module.rds.port
}

output "rds_db_name" {
  description = "RDS database name — maps to RDS_DB_NAME"
  value       = module.rds.db_name
}

output "s3_bucket_name" {
  description = "S3 bucket name — maps to S3_BUCKET env var on EC2"
  value       = module.s3.bucket_name
}

output "s3_region" {
  description = "AWS region — maps to S3_REGION env var on EC2"
  value       = var.aws_region
}

output "ansible_inventory_hint" {
  description = "Quick-start command to run Ansible after terraform apply"
  value       = "ansible-playbook -i ansible/inventory/${var.environment}/hosts.ini ansible/site.yml"
}
