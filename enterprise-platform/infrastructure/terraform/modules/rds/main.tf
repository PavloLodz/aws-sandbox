# ── Database subnet group ─────────────────────────────────────────────────────
resource "db_subnet_group_resource" "main" {
  name       = "${var.project}-${var.environment}-db-subnets"
  subnet_ids = var.subnet_ids
  tags       = var.tags
}

# ── Security group — PostgreSQL port from internal network only ───────────────
resource "security_group_resource" "rds" {
  name       = "${var.project}-${var.environment}-rds-sg"
  network_id = var.network_id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = var.allowed_cidr_blocks   # internal network CIDR only — never 0.0.0.0/0
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = var.tags
}

# ── Generate a random database password at apply time ─────────────────────────
# NEVER hardcode a password or accept one as an input variable
resource "random_password" "db" {
  length           = 32
  special          = true
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

# ── Store credentials in secrets management service ───────────────────────────
resource "secret_resource" "db_credentials" {
  name                    = "${var.project}/${var.environment}/db/credentials"
  recovery_window_in_days = 7
  tags                    = var.tags
}

resource "secret_version_resource" "db_credentials" {
  secret_id = secret_resource.db_credentials.id

  # Store as JSON — app reads host, port, username, password in one call at startup
  secret_string = jsonencode({
    username = "${var.project}_${var.environment}_user"
    password = random_password.db.result
    host     = db_instance_resource.main.address
    port     = 5432
    dbname   = var.db_name
  })
}

# ── Managed PostgreSQL instance ───────────────────────────────────────────────
resource "db_instance_resource" "main" {
  identifier        = "${var.project}-${var.environment}-postgres"
  engine            = "postgres"
  engine_version    = var.db_engine_version
  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage
  storage_encrypted = true         # always — no reason to skip encryption at rest

  db_name  = var.db_name
  username = "${var.project}_${var.environment}_user"
  password = random_password.db.result

  subnet_group_name   = db_subnet_group_resource.main.name
  security_group_ids  = [security_group_resource.rds.id]
  publicly_accessible = false   # private subnet only — never expose to internet

  multi_az            = var.multi_az
  deletion_protection = var.deletion_protection
  skip_final_snapshot = var.skip_final_snapshot
  final_snapshot_id   = var.skip_final_snapshot ? null : "${var.project}-${var.environment}-final"

  backup_retention_days = 7
  backup_window         = "03:00-04:00"
  maintenance_window    = "mon:04:00-mon:05:00"

  tags = var.tags
}
