package com.tisal.ia.ReqModel;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Respuesta estructurada que Azure debe devolver en JSON
 * Esto es lo que Azure devolverá cuando procese la intención del usuario
 */
public class AzureAiStructuredResponse {
    
    @JsonProperty("intent")
    private String intent;  // CONSULTAR_INFORMACION, AGENDAR_CITA, etc.
    
    @JsonProperty("confidence")
    private Double confidence;  // 0.0 a 1.0
    
    @JsonProperty("reasoning")
    private String reasoning;  // Por qué tomó esta decisión
    
    @JsonProperty("extracted_data")
    private ExtractedData extractedData;
    
    @JsonProperty("next_action")
    private String nextAction;  // Qué debe hacer el sistema ahora
    
    @JsonProperty("response_message")
    private String responseMessage;  // Respuesta amigable para el usuario
    
    @JsonProperty("requires_confirmation")
    private Boolean requiresConfirmation;  // ¿Necesita confirmar antes de ejecutar?
    
    public static class ExtractedData {
        @JsonProperty("rut")
        private String rut;
        
        @JsonProperty("doctor_name")
        private String doctorName;
        
        @JsonProperty("specialty")
        private String specialty;
        
        @JsonProperty("branch")
        private String branch;
        
        @JsonProperty("date")
        private String date;  // ISO 8601 format
        
        @JsonProperty("time")
        private String time;  // HH:MM format
        
        @JsonProperty("additional_context")
        private Map<String, String> additionalContext;
        
        // Getters y Setters
        public String getRut() { return rut; }
        public void setRut(String rut) { this.rut = rut; }
        
        public String getDoctorName() { return doctorName; }
        public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
        
        public String getSpecialty() { return specialty; }
        public void setSpecialty(String specialty) { this.specialty = specialty; }
        
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        
        public Map<String, String> getAdditionalContext() { return additionalContext; }
        public void setAdditionalContext(Map<String, String> additionalContext) { 
            this.additionalContext = additionalContext; 
        }
    }
    
    // Getters y Setters
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    
    public ExtractedData getExtractedData() { return extractedData; }
    public void setExtractedData(ExtractedData extractedData) { this.extractedData = extractedData; }
    
    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }
    
    public String getResponseMessage() { return responseMessage; }
    public void setResponseMessage(String responseMessage) { this.responseMessage = responseMessage; }
    
    public Boolean getRequiresConfirmation() { return requiresConfirmation; }
    public void setRequiresConfirmation(Boolean requiresConfirmation) { 
        this.requiresConfirmation = requiresConfirmation; 
    }
}
