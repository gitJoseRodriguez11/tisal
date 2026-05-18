package com.tisal.ia.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;
import com.tisal.ia.sucursales.SucursalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AzureAiService {

    private static final Logger logger = LoggerFactory.getLogger(AzureAiService.class);

    private final AzureAiProperties properties;
    private final OpenAIClient openAIClient;
    private final SucursalService sucursalService;
    private final List<TrainingExample> trainingExamples = new CopyOnWriteArrayList<>();

    public AzureAiService(AzureAiProperties properties, SucursalService sucursalService) {

        this.properties = properties;
        this.sucursalService = sucursalService;

        this.openAIClient = new OpenAIClientBuilder()
                .credential(new AzureKeyCredential(properties.getApiKey()))
                .endpoint(trimSlash(properties.getEndpoint()))
                .buildClient();
    }

    public String query(String prompt) {

        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("El prompt es obligatorio.");
        }

        validateConfiguration();

        logger.info("Ejecutando query de Azure AI con prompt de longitud {}", prompt.length());

        String fullPrompt = buildPrompt(prompt);

        ChatRequestMessage systemMessage =
                new ChatRequestSystemMessage(properties.getSystemPrompt());

        ChatRequestMessage userMessage =
                new ChatRequestUserMessage(fullPrompt);

        ChatCompletionsOptions options = new ChatCompletionsOptions(
                List.of(systemMessage, userMessage)
        )
                .setMaxTokens(900)
                .setTemperature(0.7);

        ChatCompletions response =
                openAIClient.getChatCompletions(
                        properties.getDeploymentName(),
                        options
                );

        List<ChatChoice> choices = response.getChoices();

        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException(
                    "Respuesta inesperada de Azure OpenAI: respuesta vacía."
            );
        }

        String content = choices.get(0).getMessage().getContent();

        logger.info(
                "Respuesta recibida de Azure AI con longitud {}",
                content != null ? content.length() : 0
        );

        return content != null ? content : "";
    }

    public String ping() {

        validateConfiguration();

        logger.info("Ejecutando ping a Azure OpenAI");

        ChatRequestMessage systemMessage =
                new ChatRequestSystemMessage(properties.getSystemPrompt());

        ChatRequestMessage userMessage =
                new ChatRequestUserMessage("Ping");

        ChatCompletionsOptions options = new ChatCompletionsOptions(
                List.of(systemMessage, userMessage)
        )
                .setMaxTokens(10)
                .setTemperature(0.0);

        try {

            ChatCompletions response =
                    openAIClient.getChatCompletions(
                            properties.getDeploymentName(),
                            options
                    );

            List<ChatChoice> choices = response.getChoices();

            if (choices == null || choices.isEmpty()) {
                return "No response choices returned from Azure.";
            }

            String content = choices.get(0).getMessage().getContent();

            return content != null ? content : "";

        } catch (Exception e) {

            logger.error("Ping a Azure falló: {}", e.getMessage(), e);

            return "ERROR: " + e.getMessage();
        }
    }

    public TrainingResult train(String exampleInput, String exampleOutput) {

        if (!StringUtils.hasText(exampleInput)
                || !StringUtils.hasText(exampleOutput)) {

            throw new IllegalArgumentException(
                    "El ejemplo de entrenamiento requiere input y output."
            );
        }

        TrainingExample example =
                new TrainingExample(
                        exampleInput.trim(),
                        exampleOutput.trim()
                );

        trainingExamples.add(example);

        logger.info(
                "Nuevo ejemplo de entrenamiento agregado. Total ejemplos: {}",
                trainingExamples.size()
        );

        return new TrainingResult(
                "Ejemplo de entrenamiento guardado.",
                trainingExamples.size()
        );
    }

    public TrainingResult clearTrainingExamples() {

        int removed = trainingExamples.size();

        trainingExamples.clear();

        logger.info("Se eliminaron {} ejemplos de entrenamiento", removed);

        return new TrainingResult(
                "Se eliminaron los ejemplos de entrenamiento.",
                removed
        );
    }

    public List<TrainingExample> getTrainingExamples() {

        return Collections.unmodifiableList(
                new ArrayList<>(trainingExamples)
        );
    }

    private void validateConfiguration() {

        if (!StringUtils.hasText(properties.getEndpoint())
                || !StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getDeploymentName())) {

            throw new IllegalStateException(
                    "Configure azure.ai.openai.endpoint, " +
                    "azure.ai.openai.api-key y " +
                    "azure.ai.openai.deployment-name " +
                    "en application.properties."
            );
        }
    }

    private String buildPrompt(String prompt) {

        StringBuilder builder = new StringBuilder();
        String branchReference = sucursalService.buildBranchReference(prompt);

        if (!branchReference.isBlank()) {
            builder.append(branchReference).append("\n\n");
        }

        if (trainingExamples.isEmpty()) {
            builder.append(prompt);
        } else {
            StringJoiner joiner = new StringJoiner("\n\n");
            joiner.add("Utiliza estos ejemplos de entrenamiento para responder mejor:");

            int index = 1;
            for (TrainingExample example : trainingExamples) {
                joiner.add(String.format(
                        "Ejemplo %d:\nPregunta: %s\nRespuesta: %s",
                        index++,
                        example.input(),
                        example.output()
                ));
            }

            joiner.add("Usuario: " + prompt);
            builder.append(joiner.toString());
        }

        return builder.toString();
    }

    private String trimSlash(String endpoint) {

        if (endpoint == null) {
            return "";
        }

        return endpoint.endsWith("/")
                ? endpoint.substring(0, endpoint.length() - 1)
                : endpoint;
    }

    public static final class TrainingExample {

        private final String input;
        private final String output;

        public TrainingExample(String input, String output) {
            this.input = input;
            this.output = output;
        }

        public String input() {
            return input;
        }

        public String output() {
            return output;
        }
    }

    public static final class TrainingResult {

        private final String message;
        private final int totalExamples;

        public TrainingResult(String message, int totalExamples) {
            this.message = message;
            this.totalExamples = totalExamples;
        }

        public String getMessage() {
            return message;
        }

        public int getTotalExamples() {
            return totalExamples;
        }
    }

}