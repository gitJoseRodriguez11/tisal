package com.tisal.ia.ReqModel;

public class DoctorRequest {

	 private String nombre;
     private Long especialidadId;
     private Long sucursalId;

     public String getNombre() { return nombre; }
     public void setNombre(String nombre) { this.nombre = nombre; }

     public Long getEspecialidadId() { return especialidadId; }
     public void setEspecialidadId(Long especialidadId) { this.especialidadId = especialidadId; }

     public Long getSucursalId() { return sucursalId; }
     public void setSucursalId(Long sucursalId) { this.sucursalId = sucursalId; }
 }