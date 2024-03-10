package com.setminusx.ramsey.mw.entity;

import com.setminusx.ramsey.mw.model.ClientStatus;
import com.setminusx.ramsey.mw.model.ClientType;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer clientId;
    private Integer subgraphSize;
    private Integer vertexCount;

    @Enumerated(EnumType.STRING)
    private ClientType type;

    @Enumerated(EnumType.STRING)
    private ClientStatus status;

    private LocalDateTime createdDate;
    private LocalDateTime lastPhoneHomeDate;

}
