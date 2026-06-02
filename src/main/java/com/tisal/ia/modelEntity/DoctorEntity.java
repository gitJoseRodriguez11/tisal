package com.tisal.ia.modelEntity;

import java.util.List;

import com.tisal.ia.sucursales.SucursalEntity;

import jakarta.persistence.*;
@Entity
@Table(name = "doctores")
public class DoctorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @ManyToOne
    @JoinColumn(name = "especialidad_id", nullable = false)
    private EspecialidadEntity especialidad;

    @ManyToOne
    @JoinColumn(name = "sucursal_id", nullable = false)
    private SucursalEntity sucursal;

    @Column(columnDefinition = "vector(1536)")
    private String embedding;

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DisponibilidadEntity> disponibilidades;

    public DoctorEntity() {}

    public DoctorEntity(String nombre, EspecialidadEntity especialidad, SucursalEntity sucursal) {
        this.nombre = nombre;
        this.especialidad = especialidad;
        this.sucursal = sucursal;
    }

    public DoctorEntity(Long id) {
        this.id = id;
    }

    // Getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public EspecialidadEntity getEspecialidad() { return especialidad; }
    public void setEspecialidad(EspecialidadEntity especialidad) { this.especialidad = especialidad; }

    public SucursalEntity getSucursal() { return sucursal; }
    public void setSucursal(SucursalEntity sucursal) { this.sucursal = sucursal; }

    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }

    public List<DisponibilidadEntity> getDisponibilidades() { return disponibilidades; }
    public void setDisponibilidades(List<DisponibilidadEntity> disponibilidades) { this.disponibilidades = disponibilidades; }
}
