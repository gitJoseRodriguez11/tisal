package com.tisal.ia.sucursales;


public class IsapreRequest {

    private String nombre_isapre;
    private String rut;
    private String direccion;
    private String telefono;
    private String planes;

    // --- Getters y Setters ---

    public String getNombre_isapre() {
        return nombre_isapre;
    }

    public void setNombre_isapre(String nombre_isapre) {
        this.nombre_isapre = nombre_isapre;
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

 

    // --- Opcional: toString para logging ---
    @Override
    public String toString() {
        return "IsapreRequest{" +
                "nombre_isapre='" + nombre_isapre + '\'' +
                ", rut='" + rut + '\'' +
                ", direccion='" + direccion + '\'' +
                ", telefono='" + telefono + '\'' +
                '}';
    }
}
