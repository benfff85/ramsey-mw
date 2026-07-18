package com.setminusx.ramsey.mw.repository;

import com.setminusx.ramsey.mw.entity.Fleet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FleetRepo extends JpaRepository<Fleet, String> {
}
