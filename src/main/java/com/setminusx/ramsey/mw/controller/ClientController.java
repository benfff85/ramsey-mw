package com.setminusx.ramsey.mw.controller;

import com.setminusx.ramsey.mw.dto.ClientDto;
import com.setminusx.ramsey.mw.model.ClientStatus;
import com.setminusx.ramsey.mw.model.ClientType;
import com.setminusx.ramsey.mw.service.ClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.setminusx.ramsey.mw.utility.Constants.ERROR_HEADER;

@Slf4j
@RestController
public class ClientController {

    @Autowired
    private ClientService clientService;

    @PostMapping("/api/ramsey/clients")
    public ResponseEntity registerClient(
            @RequestBody() ClientDto clientDTO) {

        log.info("Processing registerClient");

        Date date = new Date();
        clientDTO.setCreatedDate(date);
        clientDTO.setLastPhoneHomeDate(date);
        clientDTO.setStatus(ClientStatus.ACTIVE);
        log.info(clientDTO.toString());
        ClientDto createdClient = clientService.insertClient(clientDTO);
        log.info("Completed registerClient");
        return ResponseEntity.ok().body(createdClient);
    }

    @PutMapping("/api/ramsey/clients")
    public ResponseEntity updateClient(
            @RequestBody() ClientDto clientDTO) {

        log.info("Processing updateClient");

        if (clientDTO.getClientId() == null) {
            log.error("Unable to update client as there is no client id");
            return ResponseEntity.badRequest().header(ERROR_HEADER, "Client is missing an id").build();
        }

        Optional<ClientDto> existingClientDTOOptional = clientService.selectClientById(clientDTO.getClientId());
        if (!existingClientDTOOptional.isPresent()) {
            log.error("Unable to update client as there is no client with id {}", clientDTO.getClientId());
            return ResponseEntity.badRequest().header(ERROR_HEADER, "Unable to update client as there is no client with id " + clientDTO.getClientId()).build();
        }
        ClientDto existingClientDto = existingClientDTOOptional.get();
        existingClientDto.setLastPhoneHomeDate(new Date());
        existingClientDto.setStatus(clientDTO.getStatus());
        clientService.insertClient(existingClientDto);
        log.info("Completed updateClient");

        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/ramsey/clients/{id}")
    public ResponseEntity<ClientDto> getClientById(
            @PathVariable() Integer id) {

        log.info("Processing getClientById");

        Optional<ClientDto> clientDTO = clientService.selectClientById(id);
        if (!clientDTO.isPresent()) {
            log.error("Unable to get client as there is no client with id {}", id);
            return ResponseEntity.badRequest().header(ERROR_HEADER, "Unable to get client as there is no client with id " + id).build();
        }
        log.info("Completed getClientById");
        return ResponseEntity.ok().body(clientDTO.get());

    }

    @GetMapping("/api/ramsey/clients")
    public ResponseEntity<List<ClientDto>> searchForClients(
            @RequestParam() Integer subgraphSize,
            @RequestParam() Integer vertexCount,
            @RequestParam(required = false) ClientType clientType,
            @RequestParam(required = false) ClientStatus clientStatus) {

        log.info("Processing searchForClients");

        List<ClientDto> clients = clientService.getAll(subgraphSize, vertexCount);
        if(clientType != null) {
            log.info("Filtering for type: {}", clientType);
            clients = clients.parallelStream()
                    .filter(c -> clientType.equals(c.getType()))
                    .collect(Collectors.toList());
        }
        if(clientStatus != null) {
            log.info("Filtering for status: {}", clientStatus);
            clients = clients.parallelStream()
                    .filter(c -> clientStatus.equals(c.getStatus()))
                    .collect(Collectors.toList());
        }

        log.info("Completed searchForClients");
        return ResponseEntity.ok().body(clients);

    }

}