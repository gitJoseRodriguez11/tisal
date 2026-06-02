package com.tisal.ia.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.tisal.ia.modelEntity.EspecialidadEntity;

@Repository
public interface EspecialidadRepository  extends JpaRepository<EspecialidadEntity, Long>{

	// Búsqueda simple por nombre
    List<EspecialidadEntity> findByNombreContainingIgnoreCase(String nombre);

    // Búsqueda semántica usando embeddings en PostgreSQL + pgvector
    @Query(value = """
        SELECT * 
        FROM especialidades 
        ORDER BY embedding <-> CAST(:vector AS vector) 
        """, nativeQuery = true)
    List<EspecialidadEntity> buscarPorVector(@Param("vector") String vectorJson);
}
