# ── Service/IAM role for the cluster control plane ────────────────────────────
resource "iam_role_resource" "cluster" {
  name               = "${var.project}-${var.environment}-cluster-role"
  assume_role_policy = "kubernetes-cluster-service"
  # Attach cluster management policy here (provider-specific policy name/ARN).
  # Example (AWS): aws_iam_role_policy_attachment "cluster_policy" →
  #   policy_arn = "arn:aws:iam::aws:policy/AmazonEKSClusterPolicy"
}

# ── Managed Kubernetes cluster ────────────────────────────────────────────────
resource "kubernetes_cluster_resource" "main" {
  name    = "${var.project}-${var.environment}"
  version = var.kubernetes_version

  network_config {
    network_id = var.network_id
    subnet_ids = var.subnet_ids

    endpoint_private_access = true
    # endpoint_public_access is acceptable for dev/staging.
    # In prod: restrict public_access_cidrs to known IP ranges,
    # or set to false and access the cluster API via bastion/VPN only.
    endpoint_public_access = true
  }

  role_arn   = iam_role_resource.cluster.arn
  tags       = var.tags
  depends_on = [iam_role_resource.cluster]
}

# ── Service/IAM role for worker nodes ─────────────────────────────────────────
resource "iam_role_resource" "node" {
  name               = "${var.project}-${var.environment}-node-role"
  assume_role_policy = "kubernetes-worker-node"
  # Attach the following policies (provider-specific names/ARNs):
  #   - Worker node policy       (e.g. AmazonEKSWorkerNodePolicy)
  #   - CNI policy               (e.g. AmazonEKS_CNI_Policy)
  #   - Container registry read  (e.g. AmazonEC2ContainerRegistryReadOnly)
}

# ── Managed worker node group ─────────────────────────────────────────────────
resource "node_group_resource" "main" {
  cluster_name    = kubernetes_cluster_resource.main.name
  node_group_name = "${var.project}-${var.environment}-ng"
  node_role_arn   = iam_role_resource.node.arn
  subnet_ids      = var.subnet_ids   # private subnets only — nodes never placed in public subnets
  instance_type   = var.node_instance_type

  scaling_config {
    min_size     = var.node_min_size
    max_size     = var.node_max_size
    desired_size = var.node_desired_size
  }

  # max_unavailable = 1 ensures only one node drains at a time during rolling
  # upgrades, preserving cluster capacity throughout the rollout.
  update_config {
    max_unavailable = 1
  }

  tags       = var.tags
  depends_on = [iam_role_resource.node]
}

# ── Workload identity / OIDC provider ─────────────────────────────────────────
# Enables pods to assume scoped IAM / service-account roles without storing
# credentials in Kubernetes Secrets or environment variables.
# The issuer URL is emitted by the cluster after creation.
resource "oidc_provider_resource" "main" {
  issuer_url     = kubernetes_cluster_resource.main.oidc_issuer_url
  client_id_list = ["sts.provider.internal"]
  # thumbprint: derived automatically from the cluster's TLS certificate chain;
  # set explicitly when the provider requires it (e.g. aws_iam_openid_connect_provider).
}
