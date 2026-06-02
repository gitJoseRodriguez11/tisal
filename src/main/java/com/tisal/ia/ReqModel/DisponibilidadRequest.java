package com.tisal.ia.ReqModel;

import java.time.LocalTime;

public class DisponibilidadRequest {
    private Long doctorId;
    private int diaSemana;
    private LocalTime hora;

    public Long getDoctorId() { return doctorId; }
    public void setDoctorId(Long doctorId) { this.doctorId = doctorId; }

    public int getDiaSemana() { return diaSemana; }
    public void setDiaSemana(int diaSemana) { this.diaSemana = diaSemana; }

    public LocalTime getHora() { return hora; }
    public void setHora(LocalTime hora) { this.hora = hora; }
}

