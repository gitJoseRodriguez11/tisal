package com.tisal.ia.Repository;

import com.tisal.ia.modelEntity.CitaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CitaRepository extends JpaRepository<CitaEntity, Long> {
}