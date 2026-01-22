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
@lombok.RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;

    @GetMapping
    public List<Client> getClients(
            @RequestParam(required = false) Integer campaignId,
            @RequestParam(required = false) Client.Status status,
            @RequestParam(required = false) Client.Type type) {

        log.info("Fetching clients with filters - CampaignId: {}, Status: {}, Type: {}", campaignId, status, type);
        return clientService.getClients(campaignId, status, type);
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