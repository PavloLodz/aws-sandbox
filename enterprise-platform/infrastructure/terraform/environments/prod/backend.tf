# environments/prod/backend.tf
#
# Remote state backend — provider-specific values must be filled in when a provider is chosen.
# This file ships a local-backend placeholder that works out of the box for solo learning.
# Replace it before any shared or team use.
#
# ⚠  PRODUCTION WARNING: Never run production infrastructure with the local backend.
#    The local backend stores state on the filesystem with no locking, no encryption,
#    and no history. A concurrent apply or a lost laptop can destroy your production state.
#
# ── S3-compatible backend (AWS / MinIO / Cloudflare R2 / Backblaze B2) ────────
#
#   bucket     = "myapp-terraform-state"   # pre-created — see Bootstrap section in docs/terraform.md
#   key        = "prod/terraform.tfstate"  # unique per environment — separate from dev and staging state
#   encrypt    = true                       # state contains generated passwords — encryption is mandatory
#   lock_table = "myapp-terraform-locks"   # prevents concurrent applies from corrupting state
#
# ── HTTP backend (GitLab Managed Terraform / Terraform Cloud / Scalr) ─────────
#
#   address        = "https://gitlab.example.com/api/v4/projects/<id>/terraform/state/prod"
#   lock_address   = "https://gitlab.example.com/api/v4/projects/<id>/terraform/state/prod/lock"
#   unlock_address = "https://gitlab.example.com/api/v4/projects/<id>/terraform/state/prod/lock"
#
# ── Rules that always apply ───────────────────────────────────────────────────
#
# 1. The backend block cannot use variables — all values must be literals.
#    Terraform evaluates the backend before any variable resolution takes place.
# 2. Never use the local backend for shared infrastructure.
#    Multiple engineers running applies against a local state file will corrupt it.

terraform {
  backend "local" { # PLACEHOLDER — replace with object-storage backend before team use
    path = "terraform.tfstate"
  }
}
