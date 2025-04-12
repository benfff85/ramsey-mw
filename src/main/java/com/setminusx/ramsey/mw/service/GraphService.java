package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.entity.Graph;
import com.setminusx.ramsey.mw.repository.GraphRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GraphService {

    private final GraphRepo graphRepo;

    public GraphService(GraphRepo graphRepo) {
        this.graphRepo = graphRepo;
    }

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

}
