package com.mft.integration.processing;

import java.time.Instant;

/**
 * Manifest payload model describing transfer metadata published alongside transformed files.
 */
public record ManifestPayload(
        String interfaceId,
        String interfaceVersion,
        String routeId,
        String runId,
        String sourceFilename,
        String sourceChecksum,
        String destinationFilename,
        String destinationChecksum,
        long recordCount,
        Instant timestamp
) {}
