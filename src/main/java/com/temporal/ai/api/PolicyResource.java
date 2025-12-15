package com.temporal.ai.api;

import com.temporal.ai.policy.PolicyCompiler;
import com.temporal.ai.policy.PolicyCompiler.CompiledPolicy;
import com.temporal.ai.policy.PolicyCompiler.PolicyEvaluationResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/api/policy")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PolicyResource {
    
    @Inject
    PolicyCompiler policyCompiler;
    
    @POST
    @Path("/compile")
    public Response compilePolicy(PolicyRequest request) {
        try {
            CompiledPolicy compiled = policyCompiler.compilePolicy(request.policy());
            
            boolean isValid = policyCompiler.validatePolicy(compiled);
            if (!isValid) {
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "Invalid policy", "compiled", compiled))
                    .build();
            }
            
            return Response.ok(compiled).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    @POST
    @Path("/evaluate")
    public Response evaluatePolicy(PolicyEvaluationRequest request) {
        try {
            CompiledPolicy compiled = policyCompiler.compilePolicy(request.policy());
            PolicyEvaluationResult result = policyCompiler.evaluatePolicy(
                compiled,
                request.finding(),
                request.scan()
            );
            return Response.ok(result).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
    
    public record PolicyRequest(String policy) {}
    
    public record PolicyEvaluationRequest(String policy, Object finding, Object scan) {}
}
