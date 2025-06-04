package com.example;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/author")
public class HelloResource {

    @GET
    public String hello() {
        return "Hello from raj mehta!";
    }

}
