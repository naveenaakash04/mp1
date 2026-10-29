package com.smartparking.service;

import com.smartparking.algorithm.DijkstraShortestPath;
import com.smartparking.algorithm.WeightedGraph;
import com.smartparking.domain.ParkingLot;
import com.smartparking.domain.RoadSegment;
import com.smartparking.dto.TravelTimeResponse;
import com.smartparking.repository.ParkingLotRepository;
import com.smartparking.repository.RoadSegmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the road graph from the database and runs Dijkstra from the
 * simulated main entrance. The resulting travel times decide which eligible
 * parking lot is "nearest".
 */
@Service
public class TravelTimeService {

    public static final String ENTRANCE_NODE = "ENTRANCE";
    private static final int UNREACHABLE = 9999;

    private final RoadSegmentRepository roadSegmentRepository;
    private final ParkingLotRepository parkingLotRepository;

    private volatile Map<Long, Integer> cachedMinutesByLotId;

    public TravelTimeService(RoadSegmentRepository roadSegmentRepository,
                             ParkingLotRepository parkingLotRepository) {
        this.roadSegmentRepository = roadSegmentRepository;
        this.parkingLotRepository = parkingLotRepository;
    }

    /**
     * Returns a map of lot id -> shortest travel minutes from the entrance.
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> minutesByLotId() {
        Map<Long, Integer> cached = cachedMinutesByLotId;
        if (cached != null) {
            return cached;
        }
        return rebuild();
    }

    @Transactional(readOnly = true)
    public synchronized Map<Long, Integer> rebuild() {
        WeightedGraph graph = new WeightedGraph();
        graph.addNode(ENTRANCE_NODE);

        for (RoadSegment road : roadSegmentRepository.findAll()) {
            graph.addUndirectedEdge(road.getFromNode(), road.getToNode(), road.getTravelMinutes());
        }

        List<ParkingLot> lots = parkingLotRepository.findAllByOrderByCodeAsc();
        for (ParkingLot lot : lots) {
            graph.addNode(lot.getTravelNode());
        }

        Map<String, Integer> distances =
                DijkstraShortestPath.shortestDistances(graph, ENTRANCE_NODE);

        Map<Long, Integer> result = new HashMap<>();
        for (ParkingLot lot : lots) {
            result.put(lot.getId(), distances.getOrDefault(lot.getTravelNode(), UNREACHABLE));
        }
        cachedMinutesByLotId = Map.copyOf(result);
        return cachedMinutesByLotId;
    }

    public void refresh() {
        rebuild();
    }

    @Transactional(readOnly = true)
    public TravelTimeResponse response() {
        Map<Long, Integer> minutes = minutesByLotId();
        List<TravelTimeResponse.LotTravelTime> lots = parkingLotRepository.findAllByOrderByCodeAsc()
                .stream()
                .map(lot -> new TravelTimeResponse.LotTravelTime(
                        lot.getId(),
                        lot.getCode(),
                        lot.getName(),
                        minutes.getOrDefault(lot.getId(), UNREACHABLE)))
                .toList();
        return new TravelTimeResponse(ENTRANCE_NODE, lots);
    }
}
