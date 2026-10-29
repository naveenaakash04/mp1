package com.smartparking.algorithm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Small weighted, undirected graph represented as an adjacency list.
 *
 * <p>Nodes are strings such as "ENTRANCE", "LOT-A", "LOT-B". This class is
 * deliberately independent of Spring/JPA so it is easy to explain and test.</p>
 */
public class WeightedGraph {

    /**
     * A directed edge in the adjacency list. Undirected roads are stored as
     * two directed edges.
     */
    public static class Edge {
        private final String to;
        private final int weight;

        public Edge(String to, int weight) {
            this.to = to;
            this.weight = weight;
        }

        public String getTo() {
            return to;
        }

        public int getWeight() {
            return weight;
        }
    }

    private final Map<String, List<Edge>> adjacency = new HashMap<>();

    public void addNode(String node) {
        adjacency.computeIfAbsent(node, ignored -> new ArrayList<>());
    }

    public void addUndirectedEdge(String from, String to, int weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Road weight must be positive");
        }
        addNode(from);
        addNode(to);
        adjacency.get(from).add(new Edge(to, weight));
        adjacency.get(to).add(new Edge(from, weight));
    }

    public List<Edge> neighbors(String node) {
        return adjacency.getOrDefault(node, List.of());
    }

    public Set<String> nodes() {
        return adjacency.keySet();
    }
}
