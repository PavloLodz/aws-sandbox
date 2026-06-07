# infrastructure/ansible

Configuration management and operational automation.

## Structure

- `inventory/` — host groups per environment
- `group_vars/` — per-environment variable overrides
- `roles/java/` — installs JDK, sets JAVA_HOME
- `roles/postgres/` — installs and configures PostgreSQL
- `roles/monitoring/` — installs node exporter
- `playbooks/deploy.yml` — pulls image from ECR, restarts container
- `playbooks/hardening.yml` — OS security baseline
- `playbooks/rotate-secrets.yml` — rotates DB passwords

## Usage

```bash
# Deploy to dev
make ansible-deploy

# Harden dev hosts
make ansible-harden

# Encrypt a secret with ansible-vault
ansible-vault encrypt_string 'mysecret' --name 'db_password'
```

## Idempotency

Every playbook must be safe to run multiple times without side effects.
Run `ansible-lint playbooks/` before committing.
