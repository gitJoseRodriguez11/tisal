package com.tisal.ia.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tisal.ia.modelEntity.DoctorEntity;
import com.tisal.ia.modelEntity.EspecialidadEntity;
import com.tisal.ia.sucursales.SucursalEntity;

@Repository
public interface DoctorRepository extends JpaRepository<DoctorEntity, Long> {

    List<DoctorEntity> findByNombreContainingIgnoreCase(String nombre);

    @Query(value = """
        SELECT * 
        FROM doctores 
        ORDER BY embedding <-> CAST(:vector AS vector) 
        """, nativeQuery = true)
    List<DoctorEntity> buscarPorVector(@Param("vector") String vectorJson);
    
    Optional<DoctorEntity> findByNombre(String nombre);
    
    // Nuevos métodos para filtrar por relaciones
    List<DoctorEntity> findByEspecialidad(EspecialidadEntity especialidad);
    
    List<DoctorEntity> findBySucursal(SucursalEntity sucursal);

}
