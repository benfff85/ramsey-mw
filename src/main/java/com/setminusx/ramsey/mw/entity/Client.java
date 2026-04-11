package com.setminusx.ramsey.mw.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer clientId;
    private Integer campaignId;

    @Enumerated(EnumType.STRING)
    private Client.Type type;

    @Enumerated(EnumType.STRING)
    private Client.Status status;

    private LocalDateTime createdDate;
    private LocalDateTime lastPhoneHomeDate;

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    public enum Type {
        CLIQUECHECKER,
        QUEUEMANAGER,
        SIMULATED_ANNEALING,
        VARIABLE_DEPTH_SEARCH
    }

}
