package com.tisal.ia.azure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "azure.ai.openai")
public class AzureAiProperties {

    private String endpoint;
    private String apiKey;
    private String deploymentName;       // Chat
    private String embeddingDeployment;  // Embeddings
    private String apiVersion = "2024-12-01-preview";
    private String systemPrompt = "Eres un asistente clínico virtual para una red de clínicas. Usa siempre un lenguaje claro y profesional.";

    // Getters y Setters
    public String getEndpoint() {
        return endpoint;
    }
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getDeploymentName() {
        return deploymentName;
    }
    public void setDeploymentName(String deploymentName) {
        this.deploymentName = deploymentName;
    }

    public String getEmbeddingDeployment() {
        return embeddingDeployment;
    }
    public void setEmbeddingDeployment(String embeddingDeployment) {
        this.embeddingDeployment = embeddingDeployment;
    }

    public String getApiVersion() {
        return apiVersion;
    }
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
}
