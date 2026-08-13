package rip.yawn.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolverRequest(
    @NotBlank @Size(max = 200) String q
) {}
