# Ansible — Topic 5

Operational runbook for the Ansible project in `infrastructure/ansible/`.
Covers prerequisites, directory layout, inventory management, vault operations,
running playbooks, and an idempotency guide.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Directory Structure](#2-directory-structure)
3. [Inventory & group_vars](#3-inventory--group_vars)
4. [Vault Workflow](#4-vault-workflow)
5. [Running Playbooks](#5-running-playbooks)
6. [Playbook Reference](#6-playbook-reference)
7. [Roles Reference](#7-roles-reference)
8. [Idempotency Guide](#8-idempotency-guide)
9. [Ansible Commands Reference](#9-ansible-commands-reference)

---

## 1. Prerequisites

Confirm all required tools are installed before running any playbook.

```bash
# Confirm Ansible >= 2.14 is installed
ansible --version

# Confirm ansible-lint is installed
ansible-lint --version

# Confirm Docker is installed (required for deploy.yml)
docker --version

# Confirm kubectl is installed and cluster is reachable (required for rotate-secrets.yml)
kubectl version --client
kubectl cluster-info

# Confirm psql client is installed (required for rotate-secrets.yml)
psql --version
```

### SSH Key Setup

Playbooks connect to remote hosts over SSH. Keys follow this naming convention:

```
~/.ssh/myapp-<environment>.pem
```

For example: `~/.ssh/myapp-dev.pem`, `~/.ssh/myapp-staging.pem`, `~/.ssh/myapp-prod.pem`.

Each key must be set to `chmod 400`. SSH refuses to use a key file with
group- or world-readable permissions as a security safeguard:

```bash
chmod 400 ~/.ssh/myapp-dev.pem
chmod 400 ~/.ssh/myapp-staging.pem
chmod 400 ~/.ssh/myapp-prod.pem
```

> **Important:** SSH private keys must never be committed to the repository.
> Add `*.pem` to `.gitignore` and verify with `git status` before every commit.

---

## 2. Directory Structure

```
infrastructure/ansible/
├── ansible.cfg                     # Project-level defaults — picked up automatically
├── vault-password-client.sh        # CI vault password script — reads ANSIBLE_VAULT_PASSWORD
├── inventory/
│   ├── dev/hosts.yml               # Dev host group (IPs from Terraform output)
│   ├── staging/hosts.yml           # Staging host group
│   └── prod/hosts.yml              # Prod host group
├── group_vars/
│   ├── all.yml                     # Shared defaults: app_name, ports, registry URL
│   ├── dev.yml                     # Dev overrides + vault-encrypted db_password
│   ├── staging.yml                 # Staging overrides + vault-encrypted db_password
│   └── prod.yml                    # Prod overrides + vault-encrypted db_password
├── roles/
│   ├── java/                       # Installs OpenJDK 21, sets JAVA_HOME
│   ├── postgres/                   # Installs psql client, writes .pgpass
│   └── monitoring/                 # Installs node_exporter as systemd service
└── playbooks/
    ├── hardening.yml               # SSH hardening, ufw, fail2ban, audit logging
    ├── deploy.yml                  # Pull image, restart container, health check
    └── rotate-secrets.yml          # Rotate DB password, patch K8s Secret, rolling restart
```

**Key files:**

- `ansible.cfg` — Sets project-wide defaults (inventory path, vault password file,
  SSH pipelining, etc.) and is picked up automatically by Ansible when you run any
  command from within the `infrastructure/ansible/` directory.
- `vault-password-client.sh` — Shell script used as the `vault_password_file` in CI.
  It reads the `ANSIBLE_VAULT_PASSWORD` environment variable and prints it to stdout,
  allowing Ansible and ansible-lint to decrypt vault-encrypted values without a
  password file on disk.

---

## 3. Inventory & group_vars

### Environment → Group → Variables Mapping

```
inventory/dev/hosts.yml  defines group: dev
                         ↓
group_vars/dev.yml       supplies variables for all hosts in group dev
                         ↓ (merged with)
group_vars/all.yml       supplies shared defaults for every host
```

Each environment uses its own inventory directory, which means running
`ansible-playbook -i inventory/prod` targets only prod hosts — there is no
risk of accidentally running a dev playbook against production.

### Adding a New Host

Edit the relevant inventory file and add the host under the environment group:

```yaml
# inventory/dev/hosts.yml — add a second app host
all:
  children:
    dev:
      hosts:
        dev-app-01:
          ansible_host: 10.0.10.10
          ansible_user: ubuntu
          ansible_ssh_private_key_file: ~/.ssh/myapp-dev.pem
        dev-app-02:
          ansible_host: 10.0.10.11
          ansible_user: ubuntu
          ansible_ssh_private_key_file: ~/.ssh/myapp-dev.pem
```

| Field | Purpose |
|---|---|
| `ansible_host` | The IP address or DNS name Ansible connects to |
| `ansible_user` | The SSH user on the remote host (typically `ubuntu` for AWS EC2) |
| `ansible_ssh_private_key_file` | Path to the SSH private key on the control node |

### Piping Terraform Outputs into Inventory

After `terraform apply`, capture the private IPs and update `hosts.yml`:

```bash
# Get the private IP(s) from Terraform output
terraform -chdir=infrastructure/terraform/environments/dev output -json node_private_ips

# Verify Ansible can reach all hosts after updating hosts.yml
ansible all -i inventory/dev -m ping
```

---

## 4. Vault Workflow

All secrets are stored as `ansible-vault`-encrypted values in `group_vars/`.
No plaintext credentials exist anywhere in the repository.

### Encrypt a New Secret

```bash
# From infrastructure/ansible/
ansible-vault encrypt_string 'actual-secret-value' \
  --name 'db_password' >> group_vars/dev.yml
```

This appends a vault-encrypted block for `db_password` to `group_vars/dev.yml`.

### Edit an Existing Encrypted File

```bash
# Opens the encrypted file in $EDITOR — saves re-encrypted on exit
ansible-vault edit group_vars/dev.yml
```

### View an Encrypted File Without Editing

```bash
ansible-vault view group_vars/dev.yml
```

### Re-key a Vault File (Change the Vault Password)

```bash
ansible-vault rekey group_vars/dev.yml
# Prompts for the current (old) password, then the new password
```

Repeat for every `group_vars/*.yml` file that holds vault-encrypted values when
rotating the vault password.

### Verify Decryption Works

```bash
ansible-vault decrypt group_vars/dev.yml --output=-
# Prints decrypted content to stdout — confirm db_password is correct
```

### Local Vault Password File Setup

```bash
echo 'your-vault-password' > ~/.vault_pass
chmod 600 ~/.vault_pass
# ansible.cfg already points vault_password_file = ~/.vault_pass
```

`ansible.cfg` is configured with `vault_password_file = ~/.vault_pass`, so
Ansible picks up the password automatically — no `--ask-vault-pass` flag needed.

### CI Vault Password (GitHub Actions)

Set the vault password as a repository secret so `ansible-ci.yml` can decrypt
vault values during linting:

1. Navigate to `Settings → Secrets and variables → Actions → New repository secret`
2. Set **Name**: `ANSIBLE_VAULT_PASSWORD`
3. Set **Value**: the vault password

The `vault-password-client.sh` script reads `ANSIBLE_VAULT_PASSWORD` from the
environment and passes the password to Ansible automatically — no secret files
are written to the runner disk.

---

## 5. Running Playbooks

### Via Make Targets

The `Makefile` at the project root provides convenience targets for the most
common operations:

```bash
# OS hardening baseline — run once on new hosts before deploying the application
make ansible-harden

# Deploy the application container
make ansible-deploy

# Deploy a specific image tag
IMAGE_TAG=1.2.3 make ansible-deploy
```

### Via ansible-playbook Directly

Direct invocation gives full control over inventory, host targeting, and
execution mode:

```bash
# Target dev inventory
ansible-playbook playbooks/hardening.yml -i inventory/dev
ansible-playbook playbooks/deploy.yml -i inventory/dev

# Target a specific host only (useful for canary deploys or debugging)
ansible-playbook playbooks/deploy.yml -i inventory/dev --limit dev-app-01

# Dry run — show what would change without applying anything
ansible-playbook playbooks/hardening.yml -i inventory/dev --check

# Dry run with file diff output — shows exact line changes for template and
# lineinfile tasks
ansible-playbook playbooks/hardening.yml -i inventory/dev --check --diff

# Rotate secrets — targets localhost, no SSH required
ansible-playbook playbooks/rotate-secrets.yml -i inventory/dev
```

---

## 6. Playbook Reference

| Playbook | Purpose | Required vars | Target hosts | Expected duration |
|---|---|---|---|---|
| `hardening.yml` | SSH hardening, ufw, fail2ban, audit logging | none (uses group_vars) | all | ~2 min |
| `deploy.yml` | Pull image, restart container, verify health | `image_tag`, `db_password`, `container_registry` | all | ~3 min |
| `rotate-secrets.yml` | Rotate DB password, update K8s Secret, rolling restart | `db_password`, `db_host`, `db_user`, `db_name` | localhost | ~5 min |

**Notes:**

- `hardening.yml` is designed to be run once when a host is first provisioned.
  It is safe to re-run (idempotent) but only makes meaningful changes on the
  first run against a fresh host.
- `deploy.yml` pulls the image specified by `image_tag` from `container_registry`.
  Both are set in `group_vars/` and can be overridden on the command line with
  `-e image_tag=1.2.3`.
- `rotate-secrets.yml` uses `connection: local` — kubectl and psql commands run
  on the Ansible control node, not on the app hosts. The `-i inventory/dev` flag
  is still required so group_vars are loaded correctly.

---

## 7. Roles Reference

| Role | What it installs | Key defaults |
|---|---|---|
| `java` | OpenJDK 21, sets `JAVA_HOME` in `/etc/profile.d/java.sh` | `java_package: openjdk-21-jdk` |
| `postgres` | `postgresql-client`, `libpq-dev`, writes `~/.pgpass` | `postgres_version: "16"` |
| `monitoring` | Prometheus node_exporter as systemd service | `node_exporter_version: "1.8.1"`, `node_exporter_port: 9090` |

All role defaults are defined in `roles/<name>/defaults/main.yml` and can be
overridden in `group_vars/` or with `-e` on the command line.

---

## 8. Idempotency Guide

### What Idempotency Means

A playbook is **idempotent** if running it twice against the same host produces
zero changes on the second run. Idempotency is the primary correctness criterion
for all Ansible code in this project — every task must converge to the desired
state without accumulating side effects.

### How to Test Idempotency

```bash
# Run the playbook once — expect tasks to show changed or ok
ansible-playbook playbooks/hardening.yml -i inventory/dev

# Run the same playbook again immediately — ALL tasks must show ok, zero changed
ansible-playbook playbooks/hardening.yml -i inventory/dev
# Expected play recap: changed=0
```

If any task shows `changed=1` on the second run, it is not idempotent and must
be fixed before merging.

### Idempotency Patterns Used in This Project

| Pattern | Where used | Why |
|---|---|---|
| `state: present` on apt/package tasks | `roles/java`, `roles/postgres`, `hardening.yml` | apt only installs if the package is not already installed |
| `cache_valid_time: 3600` on apt update | `roles/java` | Skips `apt-get update` if the cache is less than 1 hour old |
| `changed_when: false` on command tasks | `roles/java`, `roles/postgres`, `roles/monitoring` | Verification commands (e.g. `java -version`) never count as changes |
| `stat` check before downloading | `roles/monitoring` | Skips the node_exporter download if the binary already exists on disk |
| Handlers for service restarts | `roles/monitoring`, `hardening.yml` | Services restart only when their config file actually changed — not on every run |
| `regexp:` on `lineinfile` | `hardening.yml` | Replaces an existing line rather than appending a duplicate on each run |
| `community.general.ufw` module | `hardening.yml` | The ufw module is internally idempotent — re-running it adds no duplicate firewall rules |

---

## 9. Ansible Commands Reference

| Command | Purpose |
|---|---|
| `ansible --version` | Confirm Ansible ≥ 2.14 is installed |
| `ansible-lint --version` | Confirm ansible-lint is installed |
| `ansible all -i inventory/dev -m ping` | Test SSH connectivity to all dev hosts |
| `ansible-inventory -i inventory/dev --list` | List all hosts and variables for dev |
| `ansible-playbook playbooks/hardening.yml -i inventory/dev` | Run OS hardening against dev |
| `ansible-playbook playbooks/deploy.yml -i inventory/dev` | Deploy application to dev |
| `ansible-playbook playbooks/rotate-secrets.yml -i inventory/dev` | Rotate DB password for dev |
| `ansible-playbook playbooks/hardening.yml -i inventory/dev --check` | Dry run — show changes without applying |
| `ansible-playbook playbooks/hardening.yml -i inventory/dev --check --diff` | Dry run with file diff output |
| `ansible-playbook playbooks/deploy.yml -i inventory/dev --limit dev-app-01` | Target a single host |
| `ansible-vault encrypt_string 'value' --name 'var_name'` | Encrypt a new secret value |
| `ansible-vault edit group_vars/dev.yml` | Edit an encrypted file in `$EDITOR` |
| `ansible-vault view group_vars/dev.yml` | View decrypted file contents |
| `ansible-vault rekey group_vars/dev.yml` | Change the vault password for a file |
| `ansible-vault decrypt group_vars/dev.yml --output=-` | Decrypt file to stdout |
| `ansible-lint playbooks/` | Run linter on all playbooks and referenced roles |
| `make ansible-harden` | Run `hardening.yml` via Makefile target |
| `make ansible-deploy` | Run `deploy.yml` via Makefile target |
| `IMAGE_TAG=1.2.3 make ansible-deploy` | Deploy a specific image tag |
