package com.example;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Provider
public class CORSFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // Set a specific origin instead of "*"
        responseContext.getHeaders().add("Access-Control-Allow-Origin", "http://localhost:8080");  // Specify the client origin

        // Allow specific headers and methods
        responseContext.getHeaders().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization");
        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");

        // Allow credentials
        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");

        // Optionally, set the Access-Control-Max-Age header for caching preflight responses
        responseContext.getHeaders().add("Access-Control-Max-Age", "3600");  // 1 hour
    }

}
