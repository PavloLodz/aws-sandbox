#!/bin/bash
# vault-password-client.sh
# Ansible vault password script — reads the vault password from the
# ANSIBLE_VAULT_PASSWORD environment variable.
# Used by ansible.cfg: vault_password_file = vault-password-client.sh
# Local use:  export ANSIBLE_VAULT_PASSWORD='your-vault-password'
# CI use:     set ANSIBLE_VAULT_PASSWORD as a GitHub Actions repository secret
#             (Settings → Secrets and variables → Actions → New repository secret)

echo "$ANSIBLE_VAULT_PASSWORD"
