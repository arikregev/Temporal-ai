package com.temporal.ai.api;

import com.temporal.ai.query.QueryService;
import com.temporal.ai.query.QueryService.QueryResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/query")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class QueryResource {
    
    @Inject
    QueryService queryService;
    
    @POST
    public Response processQuery(QueryRequest request) {
        try {
            QueryResponse response = queryService.processQuery(
                request.query(),
                request.team()
            );
            return Response.ok(response).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of("status", "healthy")).build();
    }
    
    public record QueryRequest(String query, String team) {}
}
