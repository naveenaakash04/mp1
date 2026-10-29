package com.smartparking.repository;

import com.smartparking.domain.Reservation;
import com.smartparking.domain.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
            select r from Reservation r
            join fetch r.vehicle
            join fetch r.space s
            join fetch s.lot
            order by r.startTime desc
            """)
    List<Reservation> findAllWithDetails();

    @Query("""
            select r from Reservation r
            join fetch r.vehicle
            join fetch r.space s
            join fetch s.lot
            where r.status in :statuses
            order by r.startTime desc
            """)
    List<Reservation> findWithDetailsByStatusIn(@Param("statuses") Collection<ReservationStatus> statuses);

    @Query("""
            select r from Reservation r
            join fetch r.vehicle
            join fetch r.space s
            join fetch s.lot
            where r.id = :id
            """)
    Optional<Reservation> findByIdWithDetails(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    /**
     * Overlap check for one space. Uses the half-open interval rule:
     * existing.start < requested.end AND existing.end > requested.start.
     */
    @Query("""
            select count(r) from Reservation r
            where r.space.id = :spaceId
              and r.status in :statuses
              and r.startTime < :end
              and r.endTime > :start
            """)
    long countOverlapping(@Param("spaceId") Long spaceId,
                          @Param("start") LocalDateTime start,
                          @Param("end") LocalDateTime end,
                          @Param("statuses") Collection<ReservationStatus> statuses);

    @Query("""
            select count(r) from Reservation r
            where r.vehicle.id = :vehicleId
              and r.status in :statuses
              and r.startTime < :end
              and r.endTime > :start
            """)
    long countOverlappingForVehicle(@Param("vehicleId") Long vehicleId,
                                    @Param("start") LocalDateTime start,
                                    @Param("end") LocalDateTime end,
                                    @Param("statuses") Collection<ReservationStatus> statuses);

    List<Reservation> findByStatusAndStartTimeBeforeOrderByStartTimeAsc(
            ReservationStatus status, LocalDateTime cutoff);

    long countByStatusIn(Collection<ReservationStatus> statuses);
}
