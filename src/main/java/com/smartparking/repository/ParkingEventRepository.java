package com.smartparking.repository;

import com.smartparking.domain.ParkingEvent;
import com.smartparking.domain.enums.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ParkingEventRepository extends JpaRepository<ParkingEvent, Long> {

    List<ParkingEvent> findTop200ByOrderByOccurredAtDesc();

    List<ParkingEvent> findByEventTypeOrderByOccurredAtDesc(EventType eventType);

    @Query("""
            select e from ParkingEvent e
            where lower(e.vehicleNumber) like lower(concat('%', :q, '%'))
               or lower(e.message) like lower(concat('%', :q, '%'))
               or lower(e.spaceCode) like lower(concat('%', :q, '%'))
            order by e.occurredAt desc
            """)
    List<ParkingEvent> search(@Param("q") String q);
}
