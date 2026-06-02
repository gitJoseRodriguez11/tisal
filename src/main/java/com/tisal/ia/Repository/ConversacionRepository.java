package com.tisal.ia.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tisal.ia.modelEntity.ConversacionEntity;

import java.util.List;

@Repository
public interface ConversacionRepository extends JpaRepository<ConversacionEntity, Long> {

    // Recupera todas las conversaciones de una sesión
    List<ConversacionEntity> findBySessionIdOrderByTimestampAsc(String sessionId);

    // Recupera las últimas N conversaciones de una sesión (ejemplo: 5)
    List<ConversacionEntity> findTop5BySessionIdOrderByTimestampDesc(String sessionId);
}
