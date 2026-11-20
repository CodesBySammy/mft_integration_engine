package com.mft.integration.config;

import com.mft.integration.connection.AuthenticationType;
import com.mft.integration.connection.ConnectionDefinition;
import com.mft.integration.connection.ConnectionResolver;
import com.mft.integration.connection.HostKeyVerificationMode;
import com.mft.integration.exception.SftpConnectionException;
import com.mft.integration.security.CredentialProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.integration.file.remote.session.CachingSessionFactory;
import org.springframework.integration.file.remote.session.SessionFactory;
import org.springframework.integration.sftp.session.DefaultSftpSessionFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages and caches SFTP SessionFactory instances keyed by connectionId.
 * Uses Apache MINA SSHD through Spring Integration SFTP.
 * Automatically wraps session factories in CachingSessionFactory to reuse
 * pooled SSH sessions.
 */
@Component
public class SftpSessionFactoryRegistry {

    private static final Logger log = LoggerFactory.getLogger(SftpSessionFactoryRegistry.class);

    private final ConnectionResolver connectionResolver;
    private final CredentialProvider credentialProvider;
    private final Map<String, SessionFactory<SftpClient.DirEntry>> factoryCache = new ConcurrentHashMap<>();

    public SftpSessionFactoryRegistry(ConnectionResolver connectionResolver,
            CredentialProvider credentialProvider) {
        this.connectionResolver = connectionResolver;
        this.credentialProvider = credentialProvider;
    }

    /**
     * Resolves or creates a cached SessionFactory for the given connection ID.
     */
    public SessionFactory<SftpClient.DirEntry> getSessionFactory(String connectionId) {
        return factoryCache.computeIfAbsent(connectionId, this::createSessionFactory);
    }

    /**
     * Creates a new SessionFactory based on the connection definition.
     */
    public SessionFactory<SftpClient.DirEntry> createSessionFactory(String connectionId) {
        ConnectionDefinition def = connectionResolver.getConnection(connectionId);
        log.info("Creating SFTP SessionFactory for connection [{}] ({}:{})", connectionId, def.getHost(),
                def.getPort());

        DefaultSftpSessionFactory rawFactory = new DefaultSftpSessionFactory(true);
        rawFactory.setHost(def.getHost());
        rawFactory.setPort(def.getPort());
        rawFactory.setUser(def.getUsername());
        rawFactory.setTimeout(def.getConnectionTimeoutMs());

        // Credential resolution
        if (def.getAuthenticationType() == AuthenticationType.PASSWORD) {
            String password = credentialProvider.resolvePassword(def);
            rawFactory.setPassword(password);
        } else if (def.getAuthenticationType() == AuthenticationType.PRIVATE_KEY) {
            String keyPath = credentialProvider.resolvePrivateKey(def);
            rawFactory.setPrivateKey(new FileSystemResource(new File(keyPath)));
            if (def.getPrivateKeyPassphrase() != null && !def.getPrivateKeyPassphrase().isBlank()) {
                rawFactory.setPrivateKeyPassphrase(def.getPrivateKeyPassphrase());
            }
        }

        // Host key verification configuration
        if (def.getHostKeyVerificationMode() == HostKeyVerificationMode.ACCEPT_ALL_DEV_ONLY) {
            log.warn(
                    "SFTP Connection [{}] is configured with ACCEPT_ALL_DEV_ONLY host-key verification. Not safe for production.",
                    connectionId);
            rawFactory.setAllowUnknownKeys(true);
        } else {
            rawFactory.setAllowUnknownKeys(false);
            if (def.getKnownHostsFile() != null && !def.getKnownHostsFile().isBlank()) {
                File knownHosts = new File(def.getKnownHostsFile());
                if (knownHosts.exists()) {
                    rawFactory.setKnownHostsResource(new FileSystemResource(knownHosts));
                } else {
                    throw new SftpConnectionException(connectionId,
                            "Configured known_hosts file not found: " + def.getKnownHostsFile());
                }
            }
        }

        // Wrap in CachingSessionFactory with a pool size of 5 sessions per connection
        // endpoint
        CachingSessionFactory<SftpClient.DirEntry> cachingFactory = new CachingSessionFactory<>(rawFactory, 5);
        cachingFactory.setSessionWaitTimeout(def.getSessionTimeoutMs());

        return cachingFactory;
    }

    /**
     * Clears cached session factories and resets underlying connections.
     */
    public void evict(String connectionId) {
        SessionFactory<SftpClient.DirEntry> factory = factoryCache.remove(connectionId);
        if (factory instanceof CachingSessionFactory<?> cachingFactory) {
            cachingFactory.destroy();
            log.info("Evicted and destroyed SessionFactory for connection [{}]", connectionId);
        }
    }

    public void evictAll() {
        factoryCache.keySet().forEach(this::evict);
    }
}
