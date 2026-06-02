package com.tisal.ia.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.tisal.ia.sucursales.ConsumoResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class AzureAiService {

    private static final Logger logger = LoggerFactory.getLogger(AzureAiService.class);

    private final AzureAiProperties properties;
    private final OpenAIClient openAIClient;
    private final JdbcTemplate jdbcTemplate;

    public AzureAiService(AzureAiProperties properties, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;

        this.openAIClient = new OpenAIClientBuilder()
                .credential(new AzureKeyCredential(properties.getApiKey()))
                .endpoint(trimSlash(properties.getEndpoint()))
                .buildClient();
    }

    // Chat completions
    public String query(String prompt) {
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("El prompt es obligatorio.");
        }
        validateConfiguration();

        ChatRequestMessage systemMessage = new ChatRequestSystemMessage(properties.getSystemPrompt());
        ChatRequestMessage userMessage = new ChatRequestUserMessage(prompt);

        ChatCompletionsOptions options = new ChatCompletionsOptions(List.of(systemMessage, userMessage))
                .setMaxTokens(900)
                .setTemperature(0.7);

        ChatCompletions response = openAIClient.getChatCompletions(properties.getDeploymentName(), options);
        List<ChatChoice> choices = response.getChoices();

        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Respuesta inesperada de Azure OpenAI: respuesta vacía.");
        }
        
     // --- Log de consumo ---
        CompletionsUsage usage = response.getUsage();
        logger.info("Consumo Azure OpenAI -> promptTokens={}, completionTokens={}, totalTokens={}",
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());

        return choices.get(0).getMessage().getContent();
    }

    // Generar embeddings y devolver vector
    public List<Float> generarEmbeddings(String texto) {
        if (!StringUtils.hasText(texto)) {
            throw new IllegalArgumentException("El texto para embeddings es obligatorio.");
        }
        validateConfiguration();

        EmbeddingsOptions options = new EmbeddingsOptions(List.of(texto));
        Embeddings embeddings = openAIClient.getEmbeddings(properties.getEmbeddingDeployment(), options);

        return embeddings.getData().get(0).getEmbedding();
    }

    public void guardarSucursal(String nombre, String direccion, String horario, String telefono) {
        List<Float> vector = generarEmbeddings(nombre); // embeddings basados en el nombre
        String vectorJson = vector.toString(); // formato "[0.123, 0.456, ...]"

        String sql = "INSERT INTO sucursales (nombre, direccion, horario, telefono, embedding) " +
                     "VALUES (?, ?, ?, ?, CAST(? AS vector))";

        jdbcTemplate.update(sql, nombre, direccion, horario, telefono, vectorJson);

        logger.info("Sucursal '{}' guardada con embedding de {} dimensiones.", nombre, vector.size());
    }
    
    
    public void guardarEspecialidad(String nombre) {
        List<Float> vector = generarEmbeddings(nombre);
        String vectorJson = vector.toString();

        String sql = "INSERT INTO especialidades (nombre, embedding) VALUES (?, CAST(? AS vector))";
        jdbcTemplate.update(sql, nombre, vectorJson);

        logger.info("Especialidad '{}' guardada con embedding de {} dimensiones.", nombre, vector.size());
    }
    
    public void guardarDoctor(String nombre, Long especialidadId, Long sucursalId) {
        List<Float> vector = generarEmbeddings(nombre);
        String vectorJson = vector.toString();

        String sql = "INSERT INTO doctores (nombre, especialidad_id, sucursal_id, embedding) " +
                "VALUES (?, ?, ?, CAST(? AS vector))";
        jdbcTemplate.update(sql, nombre, especialidadId, sucursalId, vectorJson);

        logger.info("Doctor '{}' guardado con embedding de {} dimensiones.", nombre, vector.size());
    }



    // Buscar similares en Oracle
    public List<Map<String,Object>> buscarSimilares(String texto) {
        List<Float> vector = generarEmbeddings(texto);
        String vectorJson = vector.toString();

        String sql = """
            SELECT id, nombre
            FROM sucursales
            ORDER BY embedding <-> TO_VECTOR(?)
            FETCH FIRST 5 ROWS ONLY
        """;

        return jdbcTemplate.queryForList(sql, vectorJson);
    }

    // Helpers
    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getEndpoint())
                || !StringUtils.hasText(properties.getApiKey())
                || !StringUtils.hasText(properties.getDeploymentName())
                || !StringUtils.hasText(properties.getEmbeddingDeployment())) {
            throw new IllegalStateException("Configure endpoint, api-key, deployment-name y embedding-deployment en application.properties.");
        }
    }

    private String trimSlash(String endpoint) {
        if (endpoint == null) return "";
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }
    
 // Ping de prueba a Azure OpenAI
    public String ping() {
        validateConfiguration();
        try {
            ChatRequestMessage systemMessage = new ChatRequestSystemMessage(properties.getSystemPrompt());
            ChatRequestMessage userMessage = new ChatRequestUserMessage("Ping");

            ChatCompletionsOptions options = new ChatCompletionsOptions(List.of(systemMessage, userMessage))
                    .setMaxTokens(10)
                    .setTemperature(0.0);

            ChatCompletions response = openAIClient.getChatCompletions(properties.getDeploymentName(), options);
            List<ChatChoice> choices = response.getChoices();

            if (choices == null || choices.isEmpty()) {
                return "No response choices returned from Azure.";
            }
            return choices.get(0).getMessage().getContent();
        } catch (Exception e) {
            logger.error("Ping a Azure falló: {}", e.getMessage(), e);
            return "ERROR: " + e.getMessage();
        }
    }
    
    public ConsumoResponse obtenerConsumo(String prompt) {
        validateConfiguration();

        ChatRequestMessage systemMessage = new ChatRequestSystemMessage(properties.getSystemPrompt());
        ChatRequestMessage userMessage = new ChatRequestUserMessage(prompt);

        ChatCompletionsOptions options = new ChatCompletionsOptions(List.of(systemMessage, userMessage))
                .setMaxTokens(900)
                .setTemperature(0.7);

        ChatCompletions response = openAIClient.getChatCompletions(properties.getDeploymentName(), options);

        CompletionsUsage usage = response.getUsage(); // aquí sí funciona

        ConsumoResponse consumo = new ConsumoResponse();
        consumo.setPromptTokens(usage.getPromptTokens());
        consumo.setCompletionTokens(usage.getCompletionTokens());
        consumo.setTotalTokens(usage.getTotalTokens());

        return consumo;
    }


}
