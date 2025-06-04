package com.example.util;

import jakarta.ws.rs.core.Response;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for handling errors and generating standardized error responses.
 */
public class ErrorHandler {

    private static final Logger logger = Logger.getLogger(ErrorHandler.class.getName());

    /**
     * Creates a standardized error response for bad requests.
     * 
     * @param message The error message
     * @param logMessage The message to log (can be more detailed than the client-facing message)
     * @return A Response object with status 400 (Bad Request)
     */
    public static Response badRequest(String message, String logMessage) {
        logger.warning(logMessage);
        return createErrorResponse(Response.Status.BAD_REQUEST, message);
    }

    /**
     * Creates a standardized error response for unauthorized requests.
     * 
     * @param message The error message
     * @param logMessage The message to log (can be more detailed than the client-facing message)
     * @return A Response object with status 401 (Unauthorized)
     */
    public static Response unauthorized(String message, String logMessage) {
        logger.warning(logMessage);
        return createErrorResponse(Response.Status.UNAUTHORIZED, message);
    }

    /**
     * Creates a standardized error response for not found resources.
     * 
     * @param message The error message
     * @param logMessage The message to log (can be more detailed than the client-facing message)
     * @return A Response object with status 404 (Not Found)
     */
    public static Response notFound(String message, String logMessage) {
        logger.warning(logMessage);
        return createErrorResponse(Response.Status.NOT_FOUND, message);
    }

    /**
     * Creates a standardized error response for internal server errors.
     * 
     * @param message The error message
     * @param throwable The exception that caused the error
     * @return A Response object with status 500 (Internal Server Error)
     */
    public static Response serverError(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
        return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, message);
    }

    /**
     * Creates a standardized error response for internal server errors.
     * 
     * @param message The error message
     * @param logMessage The message to log (can be more detailed than the client-facing message)
     * @return A Response object with status 500 (Internal Server Error)
     */
    public static Response serverError(String message, String logMessage) {
        logger.severe(logMessage);
        return createErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, message);
    }

    /**
     * Creates a standardized error response with the given status and message.
     * 
     * @param status The HTTP status code
     * @param message The error message
     * @return A Response object with the given status and message
     */
    private static Response createErrorResponse(Response.Status status, String message) {
        ErrorResponse errorResponse = new ErrorResponse(false, message);
        return Response.status(status).entity(errorResponse).build();
    }

    /**
     * Inner class representing a standardized error response.
     */
    public static class ErrorResponse {
        public boolean success;
        public String error;

        public ErrorResponse(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }
}
