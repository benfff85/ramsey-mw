package com.setminusx.ramsey.mw.entity;

import com.setminusx.ramsey.mw.model.ClientStatus;
import com.setminusx.ramsey.mw.model.ClientType;
import lombok.Data;

import javax.persistence.*;
import java.util.Date;

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

    private Date createdDate;
    private Date lastPhoneHomeDate;

}
