package com.smartparking.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;

/**
 * A physical parking lot. A lot is composed of many parking spaces
 * (composition: the spaces cannot exist without their lot).
 */
@Entity
@Table(
        name = "parking_lots",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_lot_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_lot_travel_node", columnNames = "travel_node")
        }
)
public class ParkingLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    /**
     * Node name used by the road graph, for example "LOT-A".
     */
    @Column(name = "travel_node", nullable = false, length = 40)
    private String travelNode;

    @OneToMany(mappedBy = "lot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ParkingSpace> spaces = new ArrayList<>();

    protected ParkingLot() {
        // Required by JPA.
    }

    public ParkingLot(String code, String name, String address, String travelNode) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.travelNode = travelNode;
    }

    public void addSpace(ParkingSpace space) {
        spaces.add(space);
        space.setLot(this);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getTravelNode() {
        return travelNode;
    }

    public void setTravelNode(String travelNode) {
        this.travelNode = travelNode;
    }

    public List<ParkingSpace> getSpaces() {
        return spaces;
    }
}
