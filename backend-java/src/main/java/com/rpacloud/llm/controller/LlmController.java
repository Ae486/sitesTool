package com.rpacloud.llm.controller;

import com.rpacloud.llm.dto.GenerateDslRequest;
import com.rpacloud.llm.dto.GenerateDslResponse;
import com.rpacloud.llm.service.DslGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final DslGenerationService dslGenerationService;

    @PostMapping("/generate-dsl")
    public GenerateDslResponse generateDsl(@Valid @RequestBody GenerateDslRequest req) {
        var dsl = dslGenerationService.generateDsl(req.getDescription());
        return new GenerateDslResponse(dsl);
    }
}
