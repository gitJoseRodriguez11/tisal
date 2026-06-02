package com.tisal.ia.Repository;

import com.tisal.ia.modelEntity.PacienteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PacienteRepository extends JpaRepository<PacienteEntity, Long> {

    // Buscar paciente por RUT
    Optional<PacienteEntity> findByRut(String rut);

    // Si quieres también buscar por correo
    Optional<PacienteEntity> findByCorreo(String correo);
}
