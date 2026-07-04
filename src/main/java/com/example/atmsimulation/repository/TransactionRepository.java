package com.example.atmsimulation.repository;

import com.example.atmsimulation.model.Account;
import com.example.atmsimulation.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByAccount(Account account);

    List<Transaction> findByAccountOrderByTimestampDesc(Account account);
}
