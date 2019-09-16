package com.setminusx.ramsey.mw.service;

import com.setminusx.ramsey.mw.dto.GraphDTO;
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

    public GraphDTO getGraphByGraphId(Integer id) {
        Graph graph = graphRepo.findGraphByGraphId(id);
        return mapGraphToDTO(graph);
    }

    public List<GraphDTO> getGraphsWithMinCliqueCount(int count) {
        List<GraphDTO> graphs = new LinkedList<>();
        Pageable pageable =  PageRequest.of(0, count);
        for ( Graph graph : graphRepo.findAllByOrderByCliqueCountAsc(pageable)) {
            graphs.add(mapGraphToDTO(graph));
        }
        return graphs;
    }

    private static GraphDTO mapGraphToDTO(Graph graph) {
        GraphDTO graphDTO = new GraphDTO();
        graphDTO.setCliqueCount(graph.getCliqueCount());
        graphDTO.setEdgeData(graph.getEdgeData());
        graphDTO.setGraphId(graph.getGraphId());
        graphDTO.setIdentifiedDate(graph.getIdentifiedDate());
        graphDTO.setSubgraphSize(graph.getSubgraphSize());
        graphDTO.setVertexCount(graph.getVertexCount());
        return graphDTO;
    }

}
