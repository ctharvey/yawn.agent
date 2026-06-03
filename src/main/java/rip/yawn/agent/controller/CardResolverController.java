package rip.yawn.agent.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rip.yawn.agent.dto.ResolverRequest;
import rip.yawn.agent.dto.ResolverResponse;
import rip.yawn.agent.service.CardResolverService;

@RestController
@RequestMapping("/api/agent/card")
public class CardResolverController {

    private final CardResolverService resolverService;

    public CardResolverController(CardResolverService resolverService) {
        this.resolverService = resolverService;
    }

    @GetMapping("/resolve")
    public ResponseEntity<ResolverResponse> resolve(@Valid ResolverRequest request) {
        ResolverResponse response = resolverService.resolve(request.q());
        return ResponseEntity.ok(response);
    }
}
