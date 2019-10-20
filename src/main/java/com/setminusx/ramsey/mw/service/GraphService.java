package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.dto.GraphDto;
import com.setminusx.ramsey.mw.entity.Graph;
import com.setminusx.ramsey.mw.repository.GraphRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class GraphService {

    @Autowired private GraphRepo graphRepo;

    public GraphDto getGraphByGraphId(Integer id) {
        Graph graph = graphRepo.findGraphByGraphId(id);
        return mapGraphToDto(graph);
    }

    public List<GraphDto> getGraphsWithMinCliqueCount(int count) {
        List<GraphDto> graphs = new LinkedList<>();
        Pageable pageable =  PageRequest.of(0, count);
        for ( Graph graph : graphRepo.findAllByOrderByCliqueCountAsc(pageable)) {
            graphs.add(mapGraphToDto(graph));
        }
        return graphs;
    }

    public void publishGraph(GraphDto graphDTO) {
        graphRepo.save(mapDtoToGraph(graphDTO));
    }

    private static GraphDto mapGraphToDto(Graph graph) {
        GraphDto graphDTO = new GraphDto();
        graphDTO.setCliqueCount(graph.getCliqueCount());
        graphDTO.setEdgeData(graph.getEdgeData());
        graphDTO.setGraphId(graph.getGraphId());
        graphDTO.setIdentifiedDate(graph.getIdentifiedDate());
        graphDTO.setSubgraphSize(graph.getSubgraphSize());
        graphDTO.setVertexCount(graph.getVertexCount());
        return graphDTO;
    }

    private Graph mapDtoToGraph(GraphDto graphDTO) {
        Graph graph = new Graph();
        graph.setCliqueCount(graphDTO.getCliqueCount());
        graph.setEdgeData(graphDTO.getEdgeData());
        graph.setGraphId(graphDTO.getGraphId());
        graph.setIdentifiedDate(graphDTO.getIdentifiedDate());
        graph.setSubgraphSize(graphDTO.getSubgraphSize());
        graph.setVertexCount(graphDTO.getVertexCount());
        return graph;
    }

}
