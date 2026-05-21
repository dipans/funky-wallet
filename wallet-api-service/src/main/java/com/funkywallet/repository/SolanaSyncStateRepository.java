package com.funkywallet.repository;

import com.funkywallet.model.entity.SolanaSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SolanaSyncStateRepository extends JpaRepository<SolanaSyncState, String> {}
