package com.mft.integration.exception;

/**
 * Thrown when an execution of a route is attempted while that same route is already actively executing.
 * Prevents race conditions and duplicate concurrent processing.
 */
public class RouteExecutionLockedException extends MftException {

    public RouteExecutionLockedException(String routeId, String message) {
        super("ROUTE_EXECUTION_LOCKED", message);
    }
}
