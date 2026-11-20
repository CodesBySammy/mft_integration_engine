# SFTP Connection Configuration Guide

This document describes how SFTP connection endpoints are defined, validated, securely resolved, and cached by the MFT Integration Engine.

---

## 1. Connection Definition Schema

SFTP connections are declared under the `mft.connections` configuration namespace:

```yaml
mft:
  connections:
    C_FILE_IN:
      connection-id: C_FILE_IN
      connection-type: SFTP
      host: ${SFTP_SOURCE_HOST:localhost}
      port: ${SFTP_SOURCE_PORT:2022}
      username: ${SFTP_SOURCE_USERNAME:mft_source}
      password: ${SFTP_SOURCE_PASSWORD:source_pass}
      password-secret-ref: SFTP_SOURCE_PASSWORD
      remote-base-directory: /
      authentication-type: PASSWORD
      host-key-verification-mode: ACCEPT_ALL_DEV_ONLY
      connection-timeout-ms: 10000
      session-timeout-ms: 30000
      enabled: true

    C_FILE_OUT:
      connection-id: C_FILE_OUT
      connection-type: SFTP
      host: ${SFTP_DESTINATION_HOST:localhost}
      port: ${SFTP_DESTINATION_PORT:2222}
      username: ${SFTP_DESTINATION_USERNAME:mft_destination}
      password: ${SFTP_DESTINATION_PASSWORD:dest_pass}
      password-secret-ref: SFTP_DESTINATION_PASSWORD
      remote-base-directory: /
      authentication-type: PASSWORD
      host-key-verification-mode: ACCEPT_ALL_DEV_ONLY
      connection-timeout-ms: 10000
      session-timeout-ms: 30000
      enabled: true
```

---

## 2. Authentication Types

- **`PASSWORD`**: Password authentication. Can be supplied directly or referenced via `passwordSecretRef` pointing to an environment variable or secret store.
- **`PRIVATE_KEY`**: Key pair authentication. Private key path or inline PEM is resolved via `privateKeySecretRef`.

---

## 3. Host Key Verification Modes

- **`ACCEPT_ALL_DEV_ONLY`**: Bypasses host key verification for rapid local simulation. Emits prominent security warning logs at startup. **Forbidden in production.**
- **`KNOWN_HOSTS`**: Validates the remote SSH server's public key against a standard OpenSSH `known_hosts` file specified in `knownHostsFile`.

---

## 4. Session Factory Caching

To avoid SSH handshake overhead on every file transfer, the engine utilizes a thread-safe `SftpSessionFactoryRegistry` backed by Spring Integration's `CachingSessionFactory`:
- Default pool size: `5` concurrent sessions per connection ID.
- Idle sessions are automatically reaped or refreshed when expired.
- Connection tests use short timeouts to verify reachability without exhausting the pool.
