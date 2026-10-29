package com.smartparking.repository;

import com.smartparking.domain.ParkingLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ParkingLotRepository extends JpaRepository<ParkingLot, Long> {

    Optional<ParkingLot> findByCodeIgnoreCase(String code);

    List<ParkingLot> findAllByOrderByCodeAsc();

    @Query("""
            select distinct l from ParkingLot l
            left join fetch l.spaces
            order by l.code
            """)
    List<ParkingLot> findAllWithSpaces();
}
