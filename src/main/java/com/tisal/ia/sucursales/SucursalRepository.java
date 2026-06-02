package com.tisal.ia.sucursales;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface SucursalRepository extends JpaRepository<SucursalEntity, Long> {

    // Búsqueda simple por nombre
    List<SucursalEntity> findByNombreContainingIgnoreCase(String nombre);

    // Búsqueda semántica usando embeddings en PostgreSQL + pgvector
    @Query(value = """
        SELECT * 
        FROM sucursales 
        ORDER BY embedding <-> CAST(:vector AS vector) 
        """, nativeQuery = true)
    List<SucursalEntity> buscarPorVector(@Param("vector") String vectorJson);

    // Actualizar sucursal y recalcular embedding
    @Modifying
    @Query(value = """
        UPDATE sucursales
        SET nombre = :nombre,
            direccion = :direccion,
            horario = :horario,
            telefono = :telefono,
            servicios = :servicios,
            embedding = CAST(:vector AS vector)
        WHERE id = :id
        """, nativeQuery = true)
    void actualizarSucursal(@Param("id") Long id,
                            @Param("nombre") String nombre,
                            @Param("direccion") String direccion,
                            @Param("horario") String horario,
                            @Param("telefono") String telefono,
                            @Param("vector") String vectorJson);
    
    Optional<SucursalEntity> findByNombre(String nombre);
}
