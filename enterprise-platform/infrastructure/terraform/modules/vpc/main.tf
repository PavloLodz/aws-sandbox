# ── Network / VPC ─────────────────────────────────────────────────────────────
resource "network_resource" "main" {
  cidr_block           = var.network_cidr
  enable_dns_hostnames = true   # required by Kubernetes cluster
  enable_dns_support   = true

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-network"
  })
}

# ── Public subnets (one per availability zone) ────────────────────────────────
resource "subnet_resource" "public" {
  count                   = length(var.availability_zones)
  network_id              = network_resource.main.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-public-${count.index + 1}"
    tier = "public"
    # Note: Kubernetes load-balancer discovery tags go here when provider is chosen
    # e.g. for AWS: "kubernetes.io/role/elb" = "1"
  })
}

# ── Private subnets (one per availability zone) ───────────────────────────────
resource "subnet_resource" "private" {
  count             = length(var.availability_zones)
  network_id        = network_resource.main.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = var.availability_zones[count.index]

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-private-${count.index + 1}"
    tier = "private"
  })
}

# ── Internet gateway ──────────────────────────────────────────────────────────
resource "internet_gateway_resource" "main" {
  network_id = network_resource.main.id

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-igw"
  })
}

# ── Elastic IPs for NAT gateways (one per AZ for HA) ─────────────────────────
resource "elastic_ip_resource" "nat" {
  count = length(var.availability_zones)

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-nat-eip-${count.index + 1}"
  })
}

# ── NAT gateways — one per AZ to prevent single-AZ outage cutting all egress ──
# For dev cost savings, pass a single-element availability_zones list to get
# one NAT gateway only.
resource "nat_gateway_resource" "main" {
  count         = length(var.availability_zones)
  allocation_id = elastic_ip_resource.nat[count.index].id
  subnet_id     = subnet_resource.public[count.index].id

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-nat-${count.index + 1}"
  })

  depends_on = [internet_gateway_resource.main]
}

# ── Route tables ──────────────────────────────────────────────────────────────
# Public route table: 0.0.0.0/0 → internet gateway
resource "route_table_resource" "public" {
  network_id = network_resource.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = internet_gateway_resource.main.id
  }

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-rt-public"
  })
}

# Private route tables: one per AZ, 0.0.0.0/0 → NAT in same AZ
# Separate per-AZ route tables ensure each AZ's private subnet uses its local
# NAT — avoiding cross-AZ traffic charges and AZ-dependency.
resource "route_table_resource" "private" {
  count      = length(var.availability_zones)
  network_id = network_resource.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = nat_gateway_resource.main[count.index].id
  }

  tags = merge(var.tags, {
    Name = "${var.project}-${var.environment}-rt-private-${count.index + 1}"
  })
}

# ── Route table associations ──────────────────────────────────────────────────
resource "route_table_association_resource" "public" {
  count          = length(var.availability_zones)
  subnet_id      = subnet_resource.public[count.index].id
  route_table_id = route_table_resource.public.id
}

resource "route_table_association_resource" "private" {
  count          = length(var.availability_zones)
  subnet_id      = subnet_resource.private[count.index].id
  route_table_id = route_table_resource.private[count.index].id
}
