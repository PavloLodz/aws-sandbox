variable "environment"        { type = string }
variable "vpc_id"             { type = string }
variable "private_subnet_ids" { type = list(string) }
variable "ec2_sg_id"          { type = string }
variable "db_name"            { type = string }
variable "db_username"        { type = string }
variable "db_password"        { type = string; sensitive = true }

# ── Security group — only EC2 app server may connect ─────────────────────────
resource "aws_security_group" "rds" {
  name        = "aws-attempt-${var.environment}-rds-sg"
  description = "Allow PostgreSQL access only from app server"
  vpc_id      = var.vpc_id

  ingress {
    description     = "PostgreSQL from app server"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.ec2_sg_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "aws-attempt-${var.environment}-rds-sg" }
}

# ── Subnet group — RDS requires subnets in at least 2 AZs ────────────────────
resource "aws_db_subnet_group" "main" {
  name       = "aws-attempt-${var.environment}-rds-subnets"
  subnet_ids = var.private_subnet_ids
  tags       = { Name = "aws-attempt-${var.environment}-rds-subnets" }
}

# ── RDS PostgreSQL (free-tier: db.t3.micro, 20 GB) ────────────────────────────
resource "aws_db_instance" "postgres" {
  identifier        = "aws-attempt-${var.environment}-postgres"
  engine            = "postgres"
  engine_version    = "16"
  instance_class    = "db.t3.micro"
  allocated_storage = 20
  storage_type      = "gp2"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false   # private subnet only
  multi_az               = false   # set true for production HA

  backup_retention_period = 7
  skip_final_snapshot     = true   # set false for production

  tags = { Name = "aws-attempt-${var.environment}-postgres" }
}

# ── Outputs ───────────────────────────────────────────────────────────────────
output "endpoint" { value = aws_db_instance.postgres.address }
output "port"     { value = aws_db_instance.postgres.port }
output "db_name"  { value = aws_db_instance.postgres.db_name }
