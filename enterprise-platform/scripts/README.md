# scripts

Utility scripts for common operations.

## Files

| Script | Purpose |
|--------|---------|
| `deploy.sh` | Manual deploy shortcut outside GitOps (use sparingly) |
| `teardown.sh` | Destroys all cloud resources — prevents unexpected costs |

## Usage

```bash
chmod +x scripts/*.sh

# Manual deploy (dev only, bypasses ArgoCD)
./scripts/deploy.sh dev

# Tear down all cloud resources
./scripts/teardown.sh dev
```
