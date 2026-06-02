package com.tisal.ia.modelEntity;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "disponibilidades")
public class DisponibilidadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private DoctorEntity doctor;

    private int diaSemana; // 1=Lunes ... 7=Domingo
    private LocalTime hora;
    private String estado = "DISPONIBLE";

    public DisponibilidadEntity() {}

    public DisponibilidadEntity(DoctorEntity doctor, int diaSemana, LocalTime hora) {
        this.doctor = doctor;
        this.diaSemana = diaSemana;
        this.hora = hora;
        this.estado = "DISPONIBLE";
    }

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DoctorEntity getDoctor() {
        return doctor;
    }

    public void setDoctor(DoctorEntity doctor) {
        this.doctor = doctor;
    }

    public int getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(int diaSemana) {
        this.diaSemana = diaSemana;
    }

    public LocalTime getHora() {
        return hora;
    }

    public void setHora(LocalTime hora) {
        this.hora = hora;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
