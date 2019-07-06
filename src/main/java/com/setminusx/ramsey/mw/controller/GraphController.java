package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.dto.GraphDTO;
import com.setminusx.ramsey.mw.service.GraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.setminusx.ramsey.mw.utility.Constants.ERROR_HEADER;

@Slf4j
@RestController
public class GraphController {

    @Autowired
    private GraphService graphService;

    @GetMapping("/api/ramsey/graphs")
    public ResponseEntity<List<GraphDTO>> getGraphByType(
            @RequestParam() String type,
            @RequestParam(defaultValue = "1") String count,
            @RequestHeader() String rqid) {

        log.info("Processing getGraphByType for rqid: {}", rqid);
        if ("min".equals(type)) {
            List<GraphDTO> graphs = graphService.getGraphsWithMinCliqueCount(Integer.parseInt(count));
            return ResponseEntity.ok().body(graphs);
        }
        return ResponseEntity.badRequest().header(ERROR_HEADER, "Invalid type: " + type).build();

    }

    @GetMapping("/api/ramsey/graphs/{id}")
    public ResponseEntity<GraphDTO> getGraphByGraphId(
            @PathVariable() Integer id,
            @RequestHeader() String rqid) {

        log.info("Processing getGraphByGraphId for rqid: {}", rqid);
        GraphDTO graphDto;
        try {
            graphDto = graphService.getGraphByGraphId(id);
        } catch (Exception e) {
            return ResponseEntity.notFound().header(ERROR_HEADER, "Graph with ID " + id + " not found").build();
        }
        return ResponseEntity.ok().body(graphDto);

    }

}