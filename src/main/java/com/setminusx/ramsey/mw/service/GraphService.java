package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.Graph;
import com.setminusx.ramsey.mw.repository.GraphRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@lombok.RequiredArgsConstructor
public class GraphService {

    private final GraphRepo graphRepo;

    public Graph getGraphByGraphId(Integer id) {
        return graphRepo.findById(id).orElse(null);
    }

    public Graph createOrUpdateGraph(Graph graph) {
        return graphRepo.save(graph);
    }

    public List<Graph> getGraphs(Integer subgraphSize, Integer vertexCount, Integer count) {
        if (subgraphSize == null && vertexCount == null) {
            return graphRepo.findAll(PageRequest.of(0, count)).toList();
        } else {
            return graphRepo.findAllBySubgraphSizeAndVertexCount(subgraphSize, vertexCount, PageRequest.of(0, count));
        }
    }

    public void deleteGraph(Integer id) {
        graphRepo.deleteById(id);
    }

    /**
     * Derive a new graph by flipping specified edges.
     * Returns a new Graph object (not persisted) with graphId = null.
     * 
     * @param baseGraph   The base graph to derive from
     * @param edgesToFlip String in format "{{v1:v2},{v3:v4}}"
     * @return New Graph with edges flipped
     */
    public Graph deriveGraph(Graph baseGraph, String edgesToFlip) {
        // Parse edges from string format {{v1:v2},{v3:v4}}
        Pattern edgePattern = Pattern.compile("\\{(\\d+):(\\d+)\\}");
        Matcher matcher = edgePattern.matcher(edgesToFlip);

        // Clone the edge data
        char[] edgeData = baseGraph.getEdgeData().toCharArray();
        int vertexCount = baseGraph.getVertexCount();

        while (matcher.find()) {
            int v1 = Integer.parseInt(matcher.group(1));
            int v2 = Integer.parseInt(matcher.group(2));

            // Ensure v1 < v2 for index calculation
            if (v1 > v2) {
                int temp = v1;
                v1 = v2;
                v2 = temp;
            }

            // Calculate index in the bitstring
            // Index = v1 * (2*n - v1 - 1) / 2 + (v2 - v1 - 1)
            // where n = vertexCount
            int index = v1 * (2 * vertexCount - v1 - 1) / 2 + (v2 - v1 - 1);

            // Flip the edge (0 -> 1, 1 -> 0)
            if (index < edgeData.length) {
                edgeData[index] = edgeData[index] == '0' ? '1' : '0';
            }
        }

        // Create new graph (not persisted - graphId is null)
        Graph derivedGraph = new Graph();
        derivedGraph.setGraphId(null); // Not saved yet
        derivedGraph.setSubgraphSize(baseGraph.getSubgraphSize());
        derivedGraph.setVertexCount(baseGraph.getVertexCount());
        derivedGraph.setEdgeData(new String(edgeData));
        derivedGraph.setCliqueCount(null); // Unknown - would need to be calculated
        derivedGraph.setIdentifiedDate(new Date());

        return derivedGraph;
    }

}
