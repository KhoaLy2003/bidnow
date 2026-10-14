package com.bidnow.wallet.repository;

import com.bidnow.wallet.domain.entity.Transaction;
import com.bidnow.wallet.domain.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    List<Transaction> findTop20ByWalletIdOrderByCreatedAtDesc(UUID walletId);

    boolean existsByTypeAndReferenceId(TransactionType type, UUID referenceId);
}
