package com.setminusx.ramsey.mw.entity;

import lombok.Data;

import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Date;

@Data
@Entity
public class Client {

    @Id
    private String clientId;
    private Integer subgraphSize;
    private Integer vertexCount;
    private String type;
    private String status;
    private Date createdDate;
    private Date lastPhoneHomeDate;

}
