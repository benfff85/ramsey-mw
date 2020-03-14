package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.dto.ClientDto;
import com.setminusx.ramsey.mw.model.ClientStatus;
import com.setminusx.ramsey.mw.model.ClientType;
import com.setminusx.ramsey.mw.service.ClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class ClientController {

    private ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @PostMapping("/api/ramsey/clients")
    public ResponseEntity<ClientDto> saveClient(@RequestBody() ClientDto clientDTO) {
        log.info("Processing saveClient");
        ClientDto client = clientService.insertClient(clientDTO);
        log.info("Completed saveClient");
        return ResponseEntity.ok().body(client);
    }


    @GetMapping("/api/ramsey/clients")
    public ResponseEntity<List<ClientDto>> searchForClients(
            @RequestParam() Integer subgraphSize,
            @RequestParam() Integer vertexCount,
            @RequestParam() ClientStatus clientStatus,
            @RequestParam(required = false) ClientType clientType
    ) {

        log.info("Processing searchForClients");

        List<ClientDto> clients = clientService.getAll(subgraphSize, vertexCount, clientStatus);
        if (clientType != null) {
            log.info("Filtering for type: {}", clientType);
            clients = clients.parallelStream()
                    .filter(c -> clientType.equals(c.getType()))
                    .collect(Collectors.toList());
        }

        log.info("Completed searchForClients");
        return ResponseEntity.ok().body(clients);

    }

}