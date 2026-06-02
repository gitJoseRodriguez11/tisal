package com.tisal.ia.ReqModel;

import java.time.LocalDateTime;

public class CitaRequest {

	private Long pacienteId;
    private Long doctorId;
    private LocalDateTime fecha;

    public Long getPacienteId() { return pacienteId; }
    public void setPacienteId(Long pacienteId) { this.pacienteId = pacienteId; }

    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
