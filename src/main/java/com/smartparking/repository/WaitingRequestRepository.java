package com.smartparking.repository;

import com.smartparking.domain.WaitingRequest;
import com.smartparking.domain.enums.WaitingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface WaitingRequestRepository extends JpaRepository<WaitingRequest, Long> {

    @Query("""
            select w from WaitingRequest w
            join fetch w.vehicle
            where w.status = :status
            order by w.arrivalTime, w.id
            """)
    List<WaitingRequest> findByStatusWithVehicle(@Param("status") WaitingStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WaitingRequest w where w.id = :id")
    Optional<WaitingRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(w) from WaitingRequest w
            where w.vehicle.id = :vehicleId
              and w.status = :status
              and w.requestedStartTime < :end
              and w.requestedEndTime > :start
            """)
    long countDuplicateWaiting(@Param("vehicleId") Long vehicleId,
                               @Param("start") LocalDateTime start,
                               @Param("end") LocalDateTime end,
                               @Param("status") WaitingStatus status);

    long countByStatus(WaitingStatus status);

    List<WaitingRequest> findAllByOrderByArrivalTimeAsc();
}
