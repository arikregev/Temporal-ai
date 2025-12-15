package com.temporal.ai.api;

import com.temporal.ai.data.entity.KnowledgeBase;
import com.temporal.ai.knowledge.KnowledgeBaseService;
import com.temporal.ai.knowledge.KnowledgeBaseService.KnowledgeBaseMatch;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Path("/api/knowledge")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class KnowledgeBaseResource {
    
    @Inject
    KnowledgeBaseService knowledgeBaseService;
    
    @POST
    public Response createQA(CreateQARequest request) {
        try {
            KnowledgeBase kb = knowledgeBaseService.createQAPair(
                request.question(),
                request.answer(),
                request.createdBy(),
                request.team(),
                request.project(),
                request.contextTags()
            );
            return Response.status(Response.Status.CREATED).entity(kb).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @PUT
    @Path("/{kbId}")
    public Response updateQA(@PathParam("kbId") String kbIdStr, UpdateQARequest request) {
        try {
            UUID kbId = UUID.fromString(kbIdStr);
            KnowledgeBase kb = knowledgeBaseService.updateQAPair(
                kbId,
                request.question(),
                request.answer(),
                request.updatedBy(),
                request.contextTags()
            );
            return Response.ok(kb).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", e.getMessage()))
                .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @DELETE
    @Path("/{kbId}")
    public Response deleteQA(@PathParam("kbId") String kbIdStr) {
        try {
            UUID kbId = UUID.fromString(kbIdStr);
            knowledgeBaseService.deleteQAPair(kbId);
            return Response.ok(Map.of("message", "Knowledge base entry deleted")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @GET
    @Path("/{kbId}")
    public Response getQA(@PathParam("kbId") String kbIdStr) {
        try {
            UUID kbId = UUID.fromString(kbIdStr);
            return knowledgeBaseService.getById(kbId)
                .map(kb -> Response.ok(kb).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Knowledge base entry not found"))
                    .build());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Invalid KB ID"))
                .build();
        }
    }
    
    @GET
    public Response listQA(
        @QueryParam("team") String team,
        @QueryParam("isActive") Boolean isActive
    ) {
        try {
            List<KnowledgeBase> entries = knowledgeBaseService.getAllEntries(team, isActive);
            return Response.ok(entries).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/search")
    public Response searchQA(SearchQARequest request) {
        try {
            List<KnowledgeBaseMatch> matches = knowledgeBaseService.findMatchingAnswers(
                request.query(),
                request.team(),
                request.limit() != null ? request.limit() : 10
            );
            return Response.ok(matches).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/{kbId}/approve")
    public Response approveQA(@PathParam("kbId") String kbIdStr, ApproveQARequest request) {
        try {
            UUID kbId = UUID.fromString(kbIdStr);
            knowledgeBaseService.approveEntry(kbId, request.approvedBy());
            return Response.ok(Map.of("message", "Knowledge base entry approved")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/{kbId}/usage")
    public Response incrementUsage(@PathParam("kbId") String kbIdStr) {
        try {
            UUID kbId = UUID.fromString(kbIdStr);
            knowledgeBaseService.incrementUsageCount(kbId);
            return Response.ok(Map.of("message", "Usage count incremented")).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    public record CreateQARequest(
        String question,
        String answer,
        String createdBy,
        String team,
        String project,
        List<String> contextTags
    ) {}
    
    public record UpdateQARequest(
        String question,
        String answer,
        String updatedBy,
        List<String> contextTags
    ) {}
    
    public record SearchQARequest(String query, String team, Integer limit) {}
    
    public record ApproveQARequest(String approvedBy) {}
}
