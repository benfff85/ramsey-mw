package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.entity.Graph;
import com.setminusx.ramsey.mw.service.GraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RestController
@RequestMapping("/api/ramsey/graphs")
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }


    @GetMapping
    public List<Graph> getGraphs(
            @RequestParam() String type,
            @RequestParam() Integer subgraphSize,
            @RequestParam() Integer vertexCount,
            @RequestParam(defaultValue = "1") String count) {

        log.info("Fetching graphs with filters - SubgraphSize: {}, VertexCount: {}, Type: {}, Count: {}", subgraphSize, vertexCount, type, count);
        List<Graph> graphs;
        if ("min".equals(type)) {
            // TODO Remove once campaign is implemented
            graphs = graphService.getGraphsWithMinCliqueCount(subgraphSize, vertexCount, Integer.parseInt(count));
        } else {
            graphs = graphService.getGraphs(subgraphSize, vertexCount, Integer.parseInt(count));
        }
        return graphs;
    }

    @GetMapping("/{id}")
    public Graph getGraphByGraphId(@PathVariable() Integer id) {
        log.info("Fetching graph with ID: {}", id);

        Graph graph = graphService.getGraphByGraphId(id);

        if (graph == null) {
            log.warn("Graph with ID: {} not found", id);
            throw new ResponseStatusException(NOT_FOUND, "Graph not found");
        }

        return graph;
    }

    @PostMapping
    public Graph createGraph(@RequestBody Graph graph) {
        log.info("Creating a new graph with data: {}", graph);
        return graphService.createOrUpdateGraph(graph);
    }


    @PutMapping("/{id}")
    public Graph updateGraph(@PathVariable() Integer id, @RequestBody Graph graph) {
        log.info("Updating graph with ID: {} with data: {}", id, graph);
        graph.setGraphId(id);
        return graphService.createOrUpdateGraph(graph);
    }

    @DeleteMapping("/{id}")
    public void deleteGraph(@PathVariable Integer id) {
        log.info("Deleting graph with ID: {}", id);
        graphService.deleteGraph(id);
    }

}