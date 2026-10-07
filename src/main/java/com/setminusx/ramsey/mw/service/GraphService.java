package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.Graph;
import com.setminusx.ramsey.mw.repository.GraphRepo;
import com.setminusx.ramsey.mw.utility.GraphBits;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

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
        char[] edgeData = baseGraph.getEdgeData().toCharArray();
        GraphBits.flip(edgeData, baseGraph.getVertexCount(), edgesToFlip);

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
