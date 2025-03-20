package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.entity.Client;
import com.setminusx.ramsey.mw.service.ClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RestController
@RequestMapping("/api/ramsey/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    public List<Client> getClients(
            @RequestParam(required = false) Integer subgraphSize,
            @RequestParam(required = false) Integer vertexCount,
            @RequestParam(required = false) Integer campaignId,
            @RequestParam(required = false) Client.Status status,
            @RequestParam(required = false) Client.Type type) {

        log.info("Fetching clients with filters - SubgraphSize: {}, VertexCount: {}, CampaignId: {}, Status: {}, Type: {}", subgraphSize, vertexCount, campaignId, status, type);
        return clientService.getClients(subgraphSize, vertexCount, campaignId, status, type);
    }

    @GetMapping("/{id}")
    public Client getClientById(@PathVariable Integer id) {
        log.info("Fetching client with ID: {}", id);

        Client client = clientService.getClientById(id);

        if (client == null) {
            log.warn("Client with ID: {} not found", id);
            throw new ResponseStatusException(NOT_FOUND, "Client not found");
        }

        return client;
    }

    @PostMapping
    public Client createClient(@RequestBody Client client) {
        log.info("Creating a new client with data: {}", client);
        return clientService.createOrUpdateClient(client);
    }

    @PutMapping("/{id}")
    public Client updateClient(@PathVariable Integer id, @RequestBody Client client) {
        log.info("Updating client with ID: {} with data: {}", id, client);
        client.setClientId(id);
        return clientService.createOrUpdateClient(client);
    }

    @DeleteMapping("/{id}")
    public void deleteClient(@PathVariable Integer id) {
        log.info("Deleting client with ID: {}", id);
        clientService.deleteClient(id);
    }

}