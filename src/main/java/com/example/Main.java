package com.example;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

import java.io.IOException;
import java.net.URI;

public class Main {

    private static final URI BASE_URI = URI.create("http://localhost:9000/");
    private static final int MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final int MAX_REQUEST_SIZE = 50 * 1024 * 1024; // 50MB

    public static HttpServer startServer() {
        // Create ResourceConfig and scan the "com.example" package for resources
        final ResourceConfig config = new ResourceConfig()
                .packages("com.example")
                .register(CORSFilter.class)
                .register(MultiPartFeature.class);

        // Create and start the Grizzly HTTP server
        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(BASE_URI, config);
        
        // Configure file upload limits
        for (NetworkListener listener : server.getListeners()) {
            listener.getFileUploadProperties().setMaxFileSize(MAX_FILE_SIZE);
            listener.getFileUploadProperties().setMaxPostSize(MAX_REQUEST_SIZE);
        }

        return server;
    }

    public static void main(String[] args) throws IOException {
        final HttpServer server = startServer();
        System.out.println("Jersey app started. Visit: " + BASE_URI);
        System.out.println("Press ENTER to stop the server...");
        System.in.read();
        server.shutdownNow();
    }

}
