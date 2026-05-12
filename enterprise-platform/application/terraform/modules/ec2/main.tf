variable "environment"      { type = string }
variable "vpc_id"           { type = string }
variable "public_subnet_id" { type = string }
variable "key_pair_name"    { type = string }
variable "instance_type"    { type = string }
variable "s3_bucket_arn"    { type = string }

# ── IAM role — lets EC2 access S3 without hardcoded credentials ───────────────
resource "aws_iam_role" "app" {
  name = "aws-attempt-${var.environment}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "s3_access" {
  name = "s3-access"
  role = aws_iam_role.app.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject", "s3:ListBucket"]
      Resource = [var.s3_bucket_arn, "${var.s3_bucket_arn}/*"]
    }]
  })
}

resource "aws_iam_instance_profile" "app" {
  name = "aws-attempt-${var.environment}-profile"
  role = aws_iam_role.app.name
}

# ── Security group ────────────────────────────────────────────────────────────
resource "aws_security_group" "app" {
  name        = "aws-attempt-${var.environment}-app-sg"
  description = "Allow SSH and HTTP traffic to the app server"
  vpc_id      = var.vpc_id

  ingress {
    description = "SSH"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]   # Restrict to your IP in production
  }

  ingress {
    description = "HTTP app port"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "aws-attempt-${var.environment}-app-sg" }
}

# ── EC2 instance ──────────────────────────────────────────────────────────────
# Amazon Linux 2023 AMI (eu-central-1) — update if deploying to another region
data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_instance" "app" {
  ami                    = data.aws_ami.amazon_linux_2023.id
  instance_type          = var.instance_type
  subnet_id              = var.public_subnet_id
  key_name               = var.key_pair_name
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.app.name

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
    encrypted   = true
  }

  tags = { Name = "aws-attempt-${var.environment}-app" }
}

# ── Outputs ───────────────────────────────────────────────────────────────────
output "public_ip"   { value = aws_instance.app.public_ip }
output "instance_id" { value = aws_instance.app.id }
output "app_sg_id"   { value = aws_security_group.app.id }
