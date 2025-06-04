package com.example;

import java.util.logging.Logger;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import com.example.util.ErrorHandler;

@Path("/Logout")
public class LogoutResource {

    private static final Logger logger = Logger.getLogger(LogoutResource.class.getName());

    public static class LogoutResponse {
        public boolean success;
        public String message;
        public String redirect;
        
        public LogoutResponse() {
            this.success = false;
        }
        
        public LogoutResponse(String message, String redirect) {
            this.success = true;
            this.message = message;
            this.redirect = redirect;
        }
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response logout() {
        try {
            logger.info("Processing logout request");
            
            // In a real implementation, you would invalidate the user's session here
            // Since we're using client-side session storage, we'll just return a success response
            // The client will handle clearing the session storage
            
            LogoutResponse response = new LogoutResponse("Logout successful", "/APITest/index.jsp");
            
            logger.info("Logout successful, redirecting to login page");
            return Response.ok(response).build();
            
        } catch (Exception e) {
            return ErrorHandler.serverError("An error occurred during logout", e);
        }
    }
}
