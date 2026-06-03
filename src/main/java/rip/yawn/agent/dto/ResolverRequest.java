package rip.yawn.agent.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolverRequest(
    @NotBlank String q
) {}
