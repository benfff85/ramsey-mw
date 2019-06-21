package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.dto.GraphDTO;
import com.setminusx.ramsey.mw.service.GraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
public class RamseyController {

    @Autowired
    private GraphService graphService;

    @GetMapping("/api/ramsey/graphs")
    public ResponseEntity<List<GraphDTO>> getGraphByType(@RequestParam() String type, @RequestParam(defaultValue = "1") String count, @RequestHeader() String rqid) {

        log.info("Processing getGraphByType for rqid: " + rqid);
        if ("min".equals(type)) {
            List<GraphDTO> graphs = graphService.getGraphsWithMinCliqueCount(Integer.parseInt(count));
            return ResponseEntity.ok().body(graphs);
        }
        return ResponseEntity.badRequest().header("Error-Description", "Invalid type: " + type).build();

    }

    @GetMapping("/api/ramsey/graphs/{id}")
    public ResponseEntity<GraphDTO> getGraphByGraphId(@PathVariable(value = "id") Integer id, @RequestHeader() String rqid) {

        log.info("Processing getGraphByGraphId for rqid: " + rqid);
        GraphDTO graphDto;
        try {
            graphDto = graphService.getGraphByGraphId(id);
        } catch (Exception e) {
            return ResponseEntity.notFound().header("Error-Description", "Graph with ID " + id + " not found").build();
        }
        return ResponseEntity.ok().body(graphDto);

    }

}