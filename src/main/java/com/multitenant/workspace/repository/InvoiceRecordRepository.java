package com.multitenant.workspace.repository;

import com.multitenant.workspace.entity.InvoiceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InvoiceRecordRepository extends JpaRepository<InvoiceRecord, Long> {

    List<InvoiceRecord> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);

    long countByWorkspaceId(String workspaceId);
}
