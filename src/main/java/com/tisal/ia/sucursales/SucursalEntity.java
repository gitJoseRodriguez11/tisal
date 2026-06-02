package com.tisal.ia.sucursales;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
@Entity
@Table(name = "sucursales")
public class SucursalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String direccion;
    private String horario;
    private String telefono;

    @Column(columnDefinition = "vector(1536)")
    private String embedding;

    // Constructor vacío para JPA
    public SucursalEntity() {}

    // Constructor con datos completos
    public SucursalEntity(String nombre, String direccion, String horario, String telefono) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.horario = horario;
        this.telefono = telefono;
    }

    // 👇 Constructor con id (para relaciones en controladores)
    public SucursalEntity(Long id) {
        this.id = id;
    }

    // Getters y setters
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDireccion() { return direccion; }
    public String getHorario() { return horario; }
    public String getTelefono() { return telefono; }

    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }
}
