package com.tisal.ia.azure;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.*;
import com.azure.core.credential.AzureKeyCredential;
import com.tisal.ia.sucursales.ConsumoResponse;
import com.tisal.ia.ReqModel.AzureAiStructuredResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;

    public AzureAiService(AzureAiProperties properties, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();

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

    /**
     * NUEVO: Query con respuesta estructurada en JSON
     * La IA devolverá una respuesta en formato JSON con intención, datos extraídos y próxima acción
     */
    public AzureAiStructuredResponse queryWithStructuredResponse(String prompt, String conversationHistory) {
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("El prompt es obligatorio.");
        }
        validateConfiguration();

        String systemMessage = """
            Eres un asistente clínico virtual para una red de clínicas. Tu trabajo es:
            1. Entender la intención del usuario (consultar, agendar, buscar disponibilidad)
            2. Extraer datos relevantes (RUT, especialidad, doctor, fecha, hora)
            3. Decidir la próxima acción de forma estructurada
            4. SIEMPRE responde en JSON válido con la estructura especificada
            
            REGLAS PARA DETECTAR INTENCIÓN:
            - AGENDAR_CITA: El usuario quiere agendar/reservar una cita (palabras clave: agendar, reservar, programar cita)
            - BUSCAR_DISPONIBILIDAD: El usuario pregunta por horarios/disponibilidad (palabras clave: disponibilidad, horario, cuándo, qué hora, próximo turno)
            - CONSULTAR_SUCURSALES: El usuario pregunta por sucursales/ubicaciones SIN mencionar disponibilidad (palabras clave: sucursal, dónde, ubicación, dirección)
            - CONSULTAR_DOCTORES: El usuario pregunta por doctores/médicos SIN mencionar horarios (palabras clave: doctor, médico, doctor especialista)
            - CONSULTAR_ESPECIALIDADES: El usuario pregunta qué especialidades hay
            - CONSULTAR_INFORMACION: El usuario hace preguntas generales sobre la clínica
            - CANCELAR_CITA: El usuario quiere cancelar una cita existente
            
            Responde EXACTAMENTE en este formato JSON:
            {
                "intent": "CONSULTAR_INFORMACION|AGENDAR_CITA|BUSCAR_DISPONIBILIDAD|CONSULTAR_SUCURSALES|CONSULTAR_DOCTORES|CONSULTAR_ESPECIALIDADES|CANCELAR_CITA|DESCONOCIDA",
                "confidence": 0.0-1.0,
                "reasoning": "breve explicación de por qué determinaste esta intención",
                "extracted_data": {
                    "rut": "RUT del paciente si aplica",
                    "doctor_name": "nombre del doctor si lo menciona",
                    "specialty": "especialidad buscada",
                    "branch": "sucursal",
                    "date": "fecha en formato ISO 8601",
                    "time": "hora en formato HH:MM",
                    "additional_context": {"clave": "valor"}
                },
                "next_action": "BUSCAR_MEDICOS|CONFIRMAR_DATOS|LISTAR_DISPONIBILIDAD|REGISTRAR_PACIENTE|EJECUTAR_CITA|RESPONDER_PREGUNTA",
                "response_message": "Respuesta amigable en español para el usuario",
                "requires_confirmation": true/false
            }
            """;

        ChatRequestMessage systemMsg = new ChatRequestSystemMessage(systemMessage);
        ChatRequestMessage userMsg = new ChatRequestUserMessage(prompt + "\n\nHistorial:\n" + conversationHistory);

        ChatCompletionsOptions options = new ChatCompletionsOptions(List.of(systemMsg, userMsg))
                .setMaxTokens(1200)
                .setTemperature(0.5);  // Menor temperatura para respuestas más consistentes

        ChatCompletions response = openAIClient.getChatCompletions(properties.getDeploymentName(), options);
        List<ChatChoice> choices = response.getChoices();

        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Respuesta inesperada de Azure OpenAI: respuesta vacía.");
        }

        CompletionsUsage usage = response.getUsage();
        logger.info("Consumo Azure OpenAI (Structured) -> promptTokens={}, completionTokens={}, totalTokens={}",
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());

        String jsonResponse = choices.get(0).getMessage().getContent();
        
        try {
            // Limpiar el JSON si viene con caracteres extra (markdown, etc)
            jsonResponse = jsonResponse.trim();
            if (jsonResponse.startsWith("```json")) {
                jsonResponse = jsonResponse.substring(7);
            }
            if (jsonResponse.startsWith("```")) {
                jsonResponse = jsonResponse.substring(3);
            }
            if (jsonResponse.endsWith("```")) {
                jsonResponse = jsonResponse.substring(0, jsonResponse.length() - 3);
            }
            jsonResponse = jsonResponse.trim();
            
            AzureAiStructuredResponse structuredResponse = objectMapper.readValue(jsonResponse, AzureAiStructuredResponse.class);
            logger.info("Response parseada exitosamente. Intent: {}, Confidence: {}", 
                    structuredResponse.getIntent(), structuredResponse.getConfidence());
            
            return structuredResponse;
        } catch (Exception e) {
            logger.error("Error parseando JSON de Azure: {}", jsonResponse, e);
            // Devolver respuesta de error estructurada
            AzureAiStructuredResponse errorResponse = new AzureAiStructuredResponse();
            errorResponse.setIntent("DESCONOCIDA");
            errorResponse.setConfidence(0.0);
            errorResponse.setReasoning("Error al procesar la respuesta: " + e.getMessage());
            errorResponse.setResponseMessage("Disculpe, tuve un error procesando su solicitud. Por favor intente nuevamente.");
            errorResponse.setNextAction("RESPONDER_PREGUNTA");
            errorResponse.setRequiresConfirmation(false);
            return errorResponse;
        }
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
