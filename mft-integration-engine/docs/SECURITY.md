# Security & Credential Hardening Guide

This document defines the security policies, credential management practices, and path confinement constraints implemented in the MFT Integration Engine.

---

## 1. Zero Credential Exposure Policy

1. **No Hardcoded Passwords**: Source code, test classes, and configuration files contain zero hardcoded secrets.
2. **Environment Secret Resolution**: Secrets are resolved at runtime via `EnvironmentSecretResolver` checking system environment variables (e.g. `SFTP_SOURCE_PASSWORD`, `SFTP_DESTINATION_PASSWORD`).
3. **Sensitive Field Masking**:
   - `ConnectionDefinition.toString()` masks passwords and private keys with `********`.
   - REST API DTOs (`ConnectionDto`) completely omit sensitive fields.
   - Logs never print passwords, keys, tokens, or raw file payloads.

---

## 2. Directory Traversal Defense

The engine enforces strict anti-traversal validation via `PathValidationUtil`:
- Rejects any path containing `..`, `\`, leading/trailing whitespace, or null characters.
- Remote paths are strictly confined to the connection's configured `remoteBaseDirectory`.
- Local workspace files are confined to `workspace/` lifecycle subdirectories.

---

## 3. Host Key Verification

In enterprise production deployments, SFTP endpoints must use `HOST_KEY_VERIFICATION_MODE: KNOWN_HOSTS`:
- Configure a standard OpenSSH `known_hosts` file.
- The engine uses Apache MINA SSHD's `KnownHostsServerKeyVerifier` to guard against Man-in-the-Middle (MitM) attacks.
- `ACCEPT_ALL_DEV_ONLY` is restricted to local development profiles and logs high-priority security warnings on startup.
