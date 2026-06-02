package com.tisal.ia.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tisal.ia.modelEntity.DisponibilidadEntity;
import com.tisal.ia.modelEntity.DoctorEntity;

public interface DisponibilidadRepository extends JpaRepository<DisponibilidadEntity, Long> {

    // Spring Data JPA generará la query automáticamente
    List<DisponibilidadEntity> findByDoctorAndEstado(DoctorEntity doctor, String estado);

    // También puedes agregar variantes útiles:
    List<DisponibilidadEntity> findByDoctorAndDiaSemanaAndEstado(DoctorEntity doctor, int diaSemana, String estado);
}


