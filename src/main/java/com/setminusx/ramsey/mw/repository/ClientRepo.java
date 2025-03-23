package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClientRepo extends JpaRepository<Client, Integer> {

    @Query("SELECT c FROM Client c WHERE " +
            "(:campaignId IS NULL OR c.campaignId = :campaignId) AND " +
            "(:status IS NULL OR c.status = :status) AND " +
            "(:type IS NULL OR c.type = :type)")
    List<Client> findByCampaignIdAndStatusAndType(
            @Param("campaignId") Integer campaignId,
            @Param("status") Client.Status status,
            @Param("type") Client.Type type);

}