package com.smartparking.algorithm;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Dijkstra's shortest-path algorithm.
 *
 * <p>A min-heap ({@link PriorityQueue}) always removes the node with the
 * smallest known distance. The result maps every reachable node to its
 * shortest travel time from the source node.</p>
 */
public final class DijkstraShortestPath {

    private DijkstraShortestPath() {
        // Utility class.
    }

    public static Map<String, Integer> shortestDistances(WeightedGraph graph, String source) {
        Map<String, Integer> distance = new HashMap<>();
        for (String node : graph.nodes()) {
            distance.put(node, Integer.MAX_VALUE);
        }
        if (!distance.containsKey(source)) {
            return distance;
        }
        distance.put(source, 0);

        PriorityQueue<NodeDistance> minHeap =
                new PriorityQueue<>(Comparator.comparingInt(NodeDistance::distance));
        minHeap.add(new NodeDistance(source, 0));

        while (!minHeap.isEmpty()) {
            NodeDistance current = minHeap.poll();

            // Ignore an outdated entry (the heap can contain duplicates).
            if (current.distance() > distance.get(current.node())) {
                continue;
            }

            for (WeightedGraph.Edge edge : graph.neighbors(current.node())) {
                int candidate = current.distance() + edge.getWeight();
                if (candidate < distance.getOrDefault(edge.getTo(), Integer.MAX_VALUE)) {
                    distance.put(edge.getTo(), candidate);
                    minHeap.add(new NodeDistance(edge.getTo(), candidate));
                }
            }
        }

        return distance;
    }

    /**
     * One heap entry: a node and the best known distance to it.
     */
    public record NodeDistance(String node, int distance) {
    }
}
