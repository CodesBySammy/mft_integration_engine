package com.mft.integration.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background scheduler that automatically polls source SFTP servers
 * and executes active routes at configured intervals.
 */
@Component
@ConditionalOnProperty(prefix = "mft.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RouteScheduler {

    private static final Logger log = LoggerFactory.getLogger(RouteScheduler.class);

    private final RouteRegistry routeRegistry;
    private final RouteOrchestrator routeOrchestrator;

    public RouteScheduler(RouteRegistry routeRegistry, RouteOrchestrator routeOrchestrator) {
        this.routeRegistry = routeRegistry;
        this.routeOrchestrator = routeOrchestrator;
    }

    @Scheduled(fixedDelayString = "${mft.scheduling.fixed-delay-ms:5000}", initialDelay = 5000)
    public void pollAndExecuteRoutes() {
        for (RouteDefinition route : routeRegistry.getAllRoutes()) {
            if (route.isEnabled()) {
                try {
                    RouteExecutionResult result = routeOrchestrator.executeRoute(route.getRouteId());
                    if (result.getFilesDiscovered() > 0) {
                        log.info("Scheduled execution for [{}]: Discovered={}, Transferred={}, Skipped={}, Failed={}",
                                route.getRouteId(), result.getFilesDiscovered(), result.getFilesTransferred(),
                                result.getFilesSkipped(), result.getFilesFailed());
                    }
                } catch (com.mft.integration.exception.RouteExecutionLockedException re) {
                    log.debug("Route [{}] is already executing in another thread. Skipping scheduled poll.", route.getRouteId());
                } catch (Exception e) {
                    log.error("Scheduled execution of route [{}] failed: {}", route.getRouteId(), e.getMessage());
                }
            }
        }
    }
}
