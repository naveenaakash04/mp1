package com.smartparking.repository;

import com.smartparking.domain.Vehicle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v where v.id = :id")
    Optional<Vehicle> findByIdForUpdate(@Param("id") Long id);

    Optional<Vehicle> findByVehicleNumberIgnoreCase(String vehicleNumber);

    boolean existsByVehicleNumberIgnoreCase(String vehicleNumber);

    List<Vehicle> findAllByOrderByVehicleNumberAsc();

    @Query("""
            select v from Vehicle v
            where lower(v.vehicleNumber) like lower(concat('%', :q, '%'))
               or lower(v.ownerName) like lower(concat('%', :q, '%'))
            order by v.vehicleNumber
            """)
    List<Vehicle> search(@Param("q") String q);
}
