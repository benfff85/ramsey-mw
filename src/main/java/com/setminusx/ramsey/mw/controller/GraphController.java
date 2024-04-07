package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.dto.GraphDto;
import com.setminusx.ramsey.mw.service.GraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.setminusx.ramsey.mw.utility.Constants.ERROR_HEADER;

@Slf4j
@RestController
public class GraphController {

    private final GraphService graphService;

    public GraphController(GraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping("/api/ramsey/graphs")
    public ResponseEntity<List<GraphDto>> getGraphByType(
            @RequestParam() String type,
            @RequestParam() Integer subgraphSize,
            @RequestParam() Integer vertexCount,
            @RequestParam(defaultValue = "1") String count) {

        log.info("Processing getGraphByType");
        List<GraphDto> graphs;
        if ("min".equals(type)) {
            graphs = graphService.getGraphsWithMinCliqueCount(subgraphSize, vertexCount, Integer.parseInt(count));
        } else {
            return ResponseEntity.badRequest().header(ERROR_HEADER, "Invalid type: " + type).build();
        }
        log.info("Completed getGraphByType");
        return ResponseEntity.ok().body(graphs);
    }

    @GetMapping("/api/ramsey/graphs/{id}")
    public ResponseEntity<GraphDto> getGraphByGraphId(
            @PathVariable() Integer id) {

        log.info("Processing getGraphByGraphId for id: {}", id);
        GraphDto graphDto;
        try {
            graphDto = graphService.getGraphByGraphId(id);
        } catch (Exception e) {
            return ResponseEntity.notFound().header(ERROR_HEADER, "Graph with ID " + id + " not found").build();
        }
        log.info("Completed getGraphByGraphId");
        return ResponseEntity.ok().body(graphDto);

    }

    @PutMapping("/api/ramsey/graphs")
    public ResponseEntity<GraphDto> publishGraph(
            @RequestBody GraphDto graph) {

        log.info("Processing publishGraph");
        graph = graphService.publishGraph(graph);
        log.info("Completed publishGraph");
        return ResponseEntity.ok().body(graph);
    }

}