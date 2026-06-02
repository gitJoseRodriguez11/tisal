package com.tisal.ia.modelEntity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversacion")
public class ConversacionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 50)
    private String sessionId;


    @Column(name = "mensaje_usuario", columnDefinition = "TEXT")
    private String mensajeUsuario;


    @Column(name = "respuesta_ia", columnDefinition = "TEXT")
    private String respuestaIa;

    @Column(name = "timestamp")
    private LocalDateTime timestamp = LocalDateTime.now();

    // 👉 Getters y Setters

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }

    public String getSessionId() {
        return sessionId;
    }
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMensajeUsuario() {
        return mensajeUsuario;
    }
    public void setMensajeUsuario(String mensajeUsuario) {
        this.mensajeUsuario = mensajeUsuario;
    }

    public String getRespuestaIa() {
        return respuestaIa;
    }
    public void setRespuestaIa(String respuestaIa) {
        this.respuestaIa = respuestaIa;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
