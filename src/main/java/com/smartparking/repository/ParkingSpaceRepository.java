package com.smartparking.repository;

import com.smartparking.domain.ParkingSpace;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ParkingSpaceRepository extends JpaRepository<ParkingSpace, Long> {

    Optional<ParkingSpace> findByCodeIgnoreCase(String code);

    @Query("""
            select s from ParkingSpace s
            join fetch s.lot
            order by s.lot.code, s.floorNumber, s.code
            """)
    List<ParkingSpace> findAllWithLot();

    @Query("""
            select s from ParkingSpace s
            join fetch s.lot
            where s.lot.id = :lotId
            order by s.floorNumber, s.code
            """)
    List<ParkingSpace> findByLotIdWithLot(@Param("lotId") Long lotId);

    @Query("""
            select s from ParkingSpace s
            join fetch s.lot
            where s.id = :id
            """)
    Optional<ParkingSpace> findByIdWithLot(@Param("id") Long id);

    /**
     * Row lock used by the reservation transaction. Two users trying to book
     * the same space are serialized here; the second one re-checks overlap
     * after the first transaction commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSpace s where s.id = :id")
    Optional<ParkingSpace> findByIdForUpdate(@Param("id") Long id);

    long countByLotId(Long lotId);
}
