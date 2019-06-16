package com.setminusx.ramsey.mw.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RamseyController {

    @Autowired private GraphRepo graphRepo;

    @RequestMapping("/api/ramsey/getGraphWithMinCliqueCount")
    public Graph getGraphWithMinCliqueCount() {
        return graphRepo.getGraphWithMinCliqueCount();
    }

    @RequestMapping("/api/ramsey/getGraphById")
    public Graph getGraphById(@RequestParam(value="id", defaultValue="1") Integer id) {
        return graphRepo.findGraphById(id);
    }

    @RequestMapping("/api/ramsey/getMostRecentGraphs")
    public String getMostRecentGraphs() {
        Iterable<Graph> graphs = graphRepo.findTop20ByOrderByCliqueCountAsc();
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        for (Graph graph : graphs) {
           sb.append(graph.getCliqueCount()).append(" --- ").append(graph.getIdentifiedDate()).append("\n");
           sb.append("<br>");
        }
        sb.append("</html>");
        return sb.toString();
    }

}