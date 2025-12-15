package com.temporal.ai.api;

import com.temporal.ai.explanation.ExplanationService;
import com.temporal.ai.explanation.ExplanationService.FindingExplanation;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

@Path("/api/explanation")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ExplanationResource {
    
    @Inject
    ExplanationService explanationService;
    
    @GET
    @Path("/finding/{findingId}")
    public Response explainFinding(@PathParam("findingId") String findingIdStr) {
        try {
            UUID findingId = UUID.fromString(findingIdStr);
            FindingExplanation explanation = explanationService.explainFinding(findingId);
            return Response.ok(explanation).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Invalid finding ID: " + e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/raw")
    public Response explainFromRaw(RawExplanationRequest request) {
        try {
            FindingExplanation explanation = explanationService.explainFromRawOutput(
                request.rawOutput(),
                request.tool()
            );
            return Response.ok(explanation).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @DELETE
    @Path("/cache/{findingId}")
    public Response clearCache(@PathParam("findingId") String findingIdStr) {
        try {
            UUID findingId = UUID.fromString(findingIdStr);
            explanationService.clearCache(findingId);
            return Response.ok(Map.of("message", "Cache cleared")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    public record RawExplanationRequest(Map<String, Object> rawOutput, String tool) {}
}
