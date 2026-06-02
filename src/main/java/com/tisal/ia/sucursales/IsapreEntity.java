package com.tisal.ia.sucursales;

import jakarta.persistence.*;

@Entity
@Table(name = "GEN_ISAPRE")
public class IsapreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre_isapre;

    private String rut;

    private String direccion;

    private String telefono;


    @Column(columnDefinition = "VECTOR(1536)")
    private String embedding;

    // --- Getters y Setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre_isapre() {
        return nombre_isapre;
    }

    public void setNombre_isapre(String nombre) {
        this.nombre_isapre = nombre;
    }

    public String getRut() {
        return rut;
    }

    public void setRut(String rut) {
        this.rut = rut;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }


    public String getEmbedding() {
        return embedding;
    }

    public void setEmbedding(String embedding) {
        this.embedding = embedding;
    }

    // --- Opcional: toString para logging ---
    @Override
    public String toString() {
        return "IsapreEntity{" +
                "id=" + id +
                ", nombre='" + nombre_isapre + '\'' +
                ", rut='" + rut + '\'' +
                ", direccion='" + direccion + '\'' +
                ", telefono='" + telefono + '\'' +
                ", embedding='" + (embedding != null ? embedding.substring(0, Math.min(50, embedding.length())) + "..." : null) + '\'' +
                '}';
    }
}
