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
    private final GraphLineageService graphLineageService;

    public Graph getGraphByGraphId(Integer id) {
        return getGraphByGraphId(id, "stored");
    }

    /**
     * reconstruct = "none": the row as stored (delta rows have no edge data).
     *               "stored": rebuild edge data for delta rows from their nearest stored ancestor.
     *               "force": rebuild every lineage row from its snapshot, ignoring its own stored bits.
     * The returned object is a detached copy whenever edge data is rebuilt.
     */
    public Graph getGraphByGraphId(Integer id, String reconstruct) {
        Graph g = graphRepo.findById(id).orElse(null);
        if (g == null || "none".equals(reconstruct)) {
            return g;
        }
        boolean force = "force".equals(reconstruct);
        boolean isLineageRow = g.getLineageDepth() != null && g.getLineageDepth() > 0;
        boolean rebuild = (g.getEdgeData() == null && g.getParentGraphId() != null) || (force && isLineageRow);
        if (!rebuild) {
            return g;
        }
        Graph copy = copyOf(g);
        copy.setEdgeData(graphLineageService.edgeData(g, force
                ? GraphLineageService.Mode.FORCE : GraphLineageService.Mode.STORED));
        return copy;
    }

    /** Persist rebuilt edge data onto a delta row: the prerequisite for rolling the middleware back. */
    public Graph materialize(Integer id) {
        Graph g = graphRepo.findById(id).orElse(null);
        if (g == null || g.getEdgeData() != null) {
            return g;
        }
        g.setEdgeData(graphLineageService.edgeData(g, GraphLineageService.Mode.STORED));
        return graphRepo.save(g);
    }

    private static Graph copyOf(Graph g) {
        Graph c = new Graph();
        c.setGraphId(g.getGraphId());
        c.setSubgraphSize(g.getSubgraphSize());
        c.setVertexCount(g.getVertexCount());
        c.setEdgeData(g.getEdgeData());
        c.setCliqueCount(g.getCliqueCount());
        c.setIdentifiedDate(g.getIdentifiedDate());
        c.setParentGraphId(g.getParentGraphId());
        c.setFlippedEdges(g.getFlippedEdges());
        c.setGraphHash(g.getGraphHash());
        c.setLineageDepth(g.getLineageDepth());
        return c;
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
