package com.tisal.ia.modelEntity;

import jakarta.persistence.*;
@Entity
@Table(name = "especialidades")
public class EspecialidadEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(columnDefinition = "vector(1536)")
    private String embedding;

    // Constructor vacío para JPA
    public EspecialidadEntity() {}

    // Constructor con nombre
    public EspecialidadEntity(String nombre) {
        this.nombre = nombre;
    }

    // 👇 Constructor con id (para relaciones en controladores)
    public EspecialidadEntity(Long id) {
        this.id = id;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getEmbedding() { return embedding; }
    public void setEmbedding(String embedding) { this.embedding = embedding; }
}
