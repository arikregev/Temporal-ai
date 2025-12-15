package com.temporal.ai.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/health")
public class HealthResource {
    
    @GET
    public Response health() {
        return Response.ok(Map.of(
            "status", "UP",
            "service", "temporal-security-analyst"
        )).build();
    }
}
