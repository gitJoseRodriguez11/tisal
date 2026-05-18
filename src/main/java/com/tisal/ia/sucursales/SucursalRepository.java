package com.tisal.ia.sucursales;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SucursalRepository extends JpaRepository<SucursalEntity, Long> {

    List<SucursalEntity> findTop5ByNombreContainingIgnoreCaseOrDireccionContainingIgnoreCaseOrHorarioContainingIgnoreCase(
            String nombre,
            String direccion,
            String horario
    );
}
