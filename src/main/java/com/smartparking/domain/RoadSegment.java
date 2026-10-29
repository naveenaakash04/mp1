package com.smartparking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

/**
 * One weighted, undirected road between two graph nodes. Nodes are
 * "ENTRANCE" or a lot's travel node such as "LOT-A". The weight is the
 * simulated travel time in minutes.
 */
@Entity
@Table(
        name = "road_segments",
        uniqueConstraints = @UniqueConstraint(name = "uk_road_pair", columnNames = {"from_node", "to_node"})
)
@Check(constraints = "travel_minutes > 0")
public class RoadSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_node", nullable = false, length = 40)
    private String fromNode;

    @Column(name = "to_node", nullable = false, length = 40)
    private String toNode;

    @Column(name = "travel_minutes", nullable = false)
    private int travelMinutes;

    protected RoadSegment() {
        // Required by JPA.
    }

    public RoadSegment(String fromNode, String toNode, int travelMinutes) {
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.travelMinutes = travelMinutes;
    }

    public Long getId() {
        return id;
    }

    public String getFromNode() {
        return fromNode;
    }

    public String getToNode() {
        return toNode;
    }

    public int getTravelMinutes() {
        return travelMinutes;
    }
}
