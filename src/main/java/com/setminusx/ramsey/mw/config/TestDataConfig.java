package com.setminusx.ramsey.mw.config;

import com.setminusx.ramsey.mw.dto.GraphDto;
import com.setminusx.ramsey.mw.service.GraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;

import static java.util.Objects.nonNull;

@Slf4j

@Component
@Profile("local")
public class TestDataConfig {

    private GraphService graphService;

    public TestDataConfig(GraphService graphService) {
        this.graphService = graphService;
    }

    @PostConstruct
    private void init() {
        log.info("Fetching prod data into test region");
        String graphUri = UriComponentsBuilder.fromHttpUrl("https://www.benjaminleephoto.com/api/ramsey/graphs")
                .queryParam("type", "min")
                .queryParam("vertexCount", 288)
                .queryParam("subgraphSize", 8)
                .queryParam("count", 5)
                .toUriString();

        RestTemplate restTemplate = new RestTemplate();
        GraphDto[] graphs = restTemplate.getForObject(graphUri, GraphDto[].class);

        if (nonNull(graphs)) {
            for (GraphDto graph : graphs) {
                graphService.publishGraph(graph);
            }
        }
    }

}
