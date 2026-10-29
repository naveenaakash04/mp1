package com.smartparking.repository;

import com.smartparking.domain.RoadSegment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadSegmentRepository extends JpaRepository<RoadSegment, Long> {
}
