package com.tisal.ia.azure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@Validated
public class AzureAiController {

    private final AzureAiService azureAiService;

    public AzureAiController(AzureAiService azureAiService) {
        this.azureAiService = azureAiService;
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        String response = azureAiService.query(request.prompt());
        return ResponseEntity.ok(new QueryResponse(response));
    }

    @PostMapping("/train")
    public ResponseEntity<TrainingResponse> train(@Valid @RequestBody TrainingRequest request) {
        AzureAiService.TrainingResult result = azureAiService.train(request.exampleInput(), request.exampleOutput());
        return ResponseEntity.ok(new TrainingResponse(result.getMessage(), result.getTotalExamples()));
    }

    @GetMapping("/train/examples")
    public ResponseEntity<List<TrainingExampleResponse>> examples() {
        List<TrainingExampleResponse> result = azureAiService.getTrainingExamples().stream()
                .map(example -> new TrainingExampleResponse(example.input(), example.output()))
                .toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/train/examples")
    public ResponseEntity<TrainingResponse> clearExamples() {
        AzureAiService.TrainingResult result = azureAiService.clearTrainingExamples();
        return ResponseEntity.ok(new TrainingResponse(result.getMessage(), result.getTotalExamples()));
    }

    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        String result = azureAiService.ping();
        if (result != null && result.startsWith("ERROR:")) {
            return ResponseEntity.status(500).body(result);
        }
        return ResponseEntity.ok(result != null ? result : "");
    }

    public record QueryRequest(@NotBlank String prompt) {
    }

    public record QueryResponse(String response) {
    }

    public record TrainingRequest(@NotBlank String exampleInput, @NotBlank String exampleOutput) {
    }

    public record TrainingResponse(String message, int totalExamples) {
    }

    public record TrainingExampleResponse(String input, String output) {
    }
}
