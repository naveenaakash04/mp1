package com.smartparking.domain;

import com.smartparking.domain.enums.SpaceKind;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

/**
 * Abstract parking space. Concrete subclasses decide which vehicles fit:
 * {@link CarSpace}, {@link TwoWheelerSpace} and {@link AccessibleSpace}.
 *
 * <p>JPA single-table inheritance keeps all spaces in one table while Java
 * still gets real polymorphism. This is the main OOP demonstration of the
 * project.</p>
 */
@Entity
@Table(
        name = "parking_spaces",
        uniqueConstraints = @UniqueConstraint(name = "uk_space_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_space_lot", columnList = "lot_id"),
                @Index(name = "idx_space_lot_floor", columnList = "lot_id, floor_number")
        }
)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "space_kind", discriminatorType = DiscriminatorType.STRING, length = 20)
@Check(constraints = "floor_number >= 0")
public abstract class ParkingSpace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false, foreignKey = @ForeignKey(name = "fk_space_lot"))
    private ParkingLot lot;

    @Column(name = "floor_number", nullable = false)
    private int floorNumber;

    protected ParkingSpace() {
        // Required by JPA.
    }

    protected ParkingSpace(String code, ParkingLot lot, int floorNumber) {
        this.code = code;
        this.lot = lot;
        this.floorNumber = floorNumber;
    }

    /**
     * Vehicle compatibility is polymorphic: each subclass implements it.
     */
    public abstract boolean canAccommodate(Vehicle vehicle);

    /**
     * True for accessible spaces, which may only be used by permit holders.
     */
    public abstract boolean requiresPermit();

    public abstract SpaceKind getKind();

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public ParkingLot getLot() {
        return lot;
    }

    public void setLot(ParkingLot lot) {
        this.lot = lot;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public void setFloorNumber(int floorNumber) {
        this.floorNumber = floorNumber;
    }
}
