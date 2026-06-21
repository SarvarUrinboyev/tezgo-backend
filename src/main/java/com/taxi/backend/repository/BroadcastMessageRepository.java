package com.taxi.backend.repository;

import com.taxi.backend.model.BroadcastMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BroadcastMessageRepository extends JpaRepository<BroadcastMessage, Long> {
    Page<BroadcastMessage> findAllByOrderBySentAtDesc(Pageable pageable);
}
