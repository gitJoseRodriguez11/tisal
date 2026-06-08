package com.tisal.ia.ReqModel;

import java.time.LocalDateTime;
import java.util.List;
import com.tisal.ia.sucursales.SucursalEntity;
import com.tisal.ia.modelEntity.EspecialidadEntity;
import com.tisal.ia.modelEntity.DoctorEntity;

/**
 * Máquina de estados para manejar el flujo de conversación dinámicamente
 */
public class ConversationState {
    
    public enum Intent {
        CONSULTAR_INFORMACION,      // El usuario pregunta algo general
        BUSCAR_DISPONIBILIDAD,      // El usuario busca citas disponibles
        AGENDAR_CITA,              // El usuario quiere agendar
        CONSULTAR_SUCURSALES,      // Busca información de sucursales
        CONSULTAR_DOCTORES,        // Busca información de doctores
        CONSULTAR_ESPECIALIDADES,  // Busca información de especialidades
        CANCELAR_CITA,             // Cancela una cita existente
        DESCONOCIDA                // La IA no entiende la intención
    }
    
    public enum ConversationPhase {
        INICIO,                    // Primero contacto o sin contexto
        RECOPILANDO_DATOS,         // Está recolectando información
        CONFIRMAR_ACCION,          // Pidiendo confirmación
        EJECUTANDO_ACCION,         // Procesando acción
        COMPLETADA,                // Acción completada
        ERROR                      // Hubo un error
    }
    
    /**
     * Fases específicas del flujo de agendamiento de cita (multi-turn)
     */
    public enum AgendarCitaPhase {
        ESPERANDO_RUT,              // Paso 1: Solicitar RUT
        ESPERANDO_SUCURSAL,         // Paso 2: Listar sucursales numeradas
        ESPERANDO_ESPECIALIDAD,     // Paso 3: Listar especialidades numeradas
        ESPERANDO_DOCTOR,           // Paso 4: Listar doctores con disponibilidad
        ESPERANDO_DIA_SEMANA,       // Paso 5A: Listar días de la semana disponibles
        ESPERANDO_HORA,             // Paso 5B: Listar horas para el día seleccionado
        CONFIRMANDO_CITA,           // Paso 6: Confirmar y agendar
        COMPLETADA                  // Paso 7: Cita agendada exitosamente
    }
    
    private String sessionId;
    private Intent intent;
    private ConversationPhase phase;
    private LocalDateTime timestamp;
    
    // Datos extraídos de la conversación
    private String pacienteRut;
    private String doctorNombre;
    private String especialidadBuscada;
    private String sucursalBuscada;
    private LocalDateTime fechaSolicitada;
    private String horaStr;
    
    // Metadata
    private int turnNumber;
    private boolean confirmacionPendiente;
    private String ultimaAccion;
    
    // Estado específico del flujo de agendamiento (multi-turn)
    private AgendarCitaPhase agendarCitaPhase;
    private List<SucursalEntity> sucursalesListadas;      // Sucursales mostradas al usuario
    private List<EspecialidadEntity> especialidadesListadas;  // Especialidades mostradas
    private List<DoctorEntity> doctoresConDisponibilidad;  // Doctores mostrados con disponibilidad
    private Integer opcionSeleccionada;                    // Número seleccionado por usuario (1, 2, 3...)
    
    // Nuevos campos para el flujo de fecha/hora
    private java.util.Map<Integer, List<com.tisal.ia.modelEntity.DisponibilidadEntity>> diasDisponibles;  // dia_semana -> list of Disponibilidad
    private Integer diaSemanaSeleccionado;                // Día de la semana seleccionado (1-7)
    private List<com.tisal.ia.modelEntity.DisponibilidadEntity> horasParaDia;  // Horas disponibles para el día
    
    public ConversationState(String sessionId) {
        this.sessionId = sessionId;
        this.intent = Intent.DESCONOCIDA;
        this.phase = ConversationPhase.INICIO;
        this.timestamp = LocalDateTime.now();
        this.turnNumber = 0;
        this.confirmacionPendiente = false;
    }
    
    // Getters y Setters
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public Intent getIntent() { return intent; }
    public void setIntent(Intent intent) { this.intent = intent; }
    
    public ConversationPhase getPhase() { return phase; }
    public void setPhase(ConversationPhase phase) { this.phase = phase; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getPacienteRut() { return pacienteRut; }
    public void setPacienteRut(String pacienteRut) { this.pacienteRut = pacienteRut; }
    
    public String getDoctorNombre() { return doctorNombre; }
    public void setDoctorNombre(String doctorNombre) { this.doctorNombre = doctorNombre; }
    
    public String getEspecialidadBuscada() { return especialidadBuscada; }
    public void setEspecialidadBuscada(String especialidadBuscada) { this.especialidadBuscada = especialidadBuscada; }
    
    public String getSucursalBuscada() { return sucursalBuscada; }
    public void setSucursalBuscada(String sucursalBuscada) { this.sucursalBuscada = sucursalBuscada; }
    
    public LocalDateTime getFechaSolicitada() { return fechaSolicitada; }
    public void setFechaSolicitada(LocalDateTime fechaSolicitada) { this.fechaSolicitada = fechaSolicitada; }
    
    public String getHoraStr() { return horaStr; }
    public void setHoraStr(String horaStr) { this.horaStr = horaStr; }
    
    public int getTurnNumber() { return turnNumber; }
    public void setTurnNumber(int turnNumber) { this.turnNumber = turnNumber; }
    
    public boolean isConfirmacionPendiente() { return confirmacionPendiente; }
    public void setConfirmacionPendiente(boolean confirmacionPendiente) { this.confirmacionPendiente = confirmacionPendiente; }
    
    public String getUltimaAccion() { return ultimaAccion; }
    public void setUltimaAccion(String ultimaAccion) { this.ultimaAccion = ultimaAccion; }
    
    // Getters/Setters para flujo de agendamiento
    public AgendarCitaPhase getAgendarCitaPhase() { return agendarCitaPhase; }
    public void setAgendarCitaPhase(AgendarCitaPhase agendarCitaPhase) { this.agendarCitaPhase = agendarCitaPhase; }
    
    public List<SucursalEntity> getSucursalesListadas() { return sucursalesListadas; }
    public void setSucursalesListadas(List<SucursalEntity> sucursalesListadas) { this.sucursalesListadas = sucursalesListadas; }
    
    public List<EspecialidadEntity> getEspecialidadesListadas() { return especialidadesListadas; }
    public void setEspecialidadesListadas(List<EspecialidadEntity> especialidadesListadas) { this.especialidadesListadas = especialidadesListadas; }
    
    public List<DoctorEntity> getDoctoresConDisponibilidad() { return doctoresConDisponibilidad; }
    public void setDoctoresConDisponibilidad(List<DoctorEntity> doctoresConDisponibilidad) { this.doctoresConDisponibilidad = doctoresConDisponibilidad; }
    
    public Integer getOpcionSeleccionada() { return opcionSeleccionada; }
    public void setOpcionSeleccionada(Integer opcionSeleccionada) { this.opcionSeleccionada = opcionSeleccionada; }
    
    // Getters/Setters para flujo de fecha y hora
    public java.util.Map<Integer, List<com.tisal.ia.modelEntity.DisponibilidadEntity>> getDiasDisponibles() { return diasDisponibles; }
    public void setDiasDisponibles(java.util.Map<Integer, List<com.tisal.ia.modelEntity.DisponibilidadEntity>> diasDisponibles) { this.diasDisponibles = diasDisponibles; }
    
    public Integer getDiaSemanaSeleccionado() { return diaSemanaSeleccionado; }
    public void setDiaSemanaSeleccionado(Integer diaSemanaSeleccionado) { this.diaSemanaSeleccionado = diaSemanaSeleccionado; }
    
    public List<com.tisal.ia.modelEntity.DisponibilidadEntity> getHorasParaDia() { return horasParaDia; }
    public void setHorasParaDia(List<com.tisal.ia.modelEntity.DisponibilidadEntity> horasParaDia) { this.horasParaDia = horasParaDia; }
}
