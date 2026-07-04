package com.example.atmsimulation.service;

import com.example.atmsimulation.model.Account;
import com.example.atmsimulation.model.Transaction;
import com.example.atmsimulation.repository.AccountRepository;
import com.example.atmsimulation.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class AtmService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    // Simple in-memory tracking of failed PIN attempts and locked accounts.
    // Good enough for a classroom simulation; a real bank would persist this.
    private final Map<String, Integer> failedPinAttempts = new HashMap<>();
    private final Set<String> lockedAccounts = new HashSet<>();

    public static final BigDecimal MAX_WITHDRAWAL_PER_TRANSACTION = new BigDecimal("5000");
    public static final BigDecimal MAX_WITHDRAWAL_PER_DAY = new BigDecimal("20000");

    public AtmService(AccountRepository accountRepository,
                       TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public Account createAccount(String name, String accountNumber, String pin, BigDecimal initialBalance) {
        if (accountRepository.existsByAccountNumber(accountNumber)) {
            throw new IllegalArgumentException("Account number already exists");
        }
        Account account = new Account();
        account.setName(name);
        account.setAccountNumber(accountNumber);
        account.setPin(pin);
        account.setBalance(initialBalance != null ? initialBalance : BigDecimal.ZERO);
        account.setActive(true);

        account = accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setType(Transaction.TransactionType.ACCOUNT_CREATION);
        tx.setAmount(BigDecimal.ZERO);
        tx.setBalanceAfter(account.getBalance());
        tx.setTimestamp(LocalDateTime.now());
        transactionRepository.save(tx);

        return account;
    }

    public Optional<Account> getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber);
    }

    public boolean isAccountLocked(String accountNumber) {
        return lockedAccounts.contains(accountNumber);
    }

    public void recordFailedPinAttempt(String accountNumber) {
        int attempts = failedPinAttempts.getOrDefault(accountNumber, 0) + 1;
        failedPinAttempts.put(accountNumber, attempts);
        if (attempts >= 3) {
            lockedAccounts.add(accountNumber);
        }
    }

    public void resetFailedPinAttempts(String accountNumber) {
        failedPinAttempts.remove(accountNumber);
    }

    public boolean verifyPin(String accountNumber, String pin) {
        Optional<Account> opt = accountRepository.findByAccountNumber(accountNumber);
        if (opt.isEmpty()) {
            return false;
        }
        Account account = opt.get();
        if (!account.isActive()) {
            return false;
        }
        if (isAccountLocked(accountNumber)) {
            return false;
        }
        if (!account.getPin().equals(pin)) {
            recordFailedPinAttempt(accountNumber);
            return false;
        }
        resetFailedPinAttempts(accountNumber);
        return true;
    }

    public boolean changePin(String accountNumber, String oldPin, String newPin) {
        Optional<Account> opt = accountRepository.findByAccountNumber(accountNumber);
        if (opt.isEmpty() || !opt.get().getPin().equals(oldPin) || !opt.get().isActive()) {
            return false;
        }
        Account account = opt.get();
        account.setPin(newPin);
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setType(Transaction.TransactionType.PIN_CHANGE);
        tx.setAmount(BigDecimal.ZERO);
        tx.setBalanceAfter(account.getBalance());
        tx.setTimestamp(LocalDateTime.now());
        transactionRepository.save(tx);

        return true;
    }

    public BigDecimal getBalance(String accountNumber) {
        Optional<Account> opt = accountRepository.findByAccountNumber(accountNumber);
        if (opt.isEmpty() || !opt.get().isActive()) {
            return null;
        }
        Account account = opt.get();

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setType(Transaction.TransactionType.BALANCE_INQUIRY);
        tx.setAmount(BigDecimal.ZERO);
        tx.setBalanceAfter(account.getBalance());
        tx.setTimestamp(LocalDateTime.now());
        transactionRepository.save(tx);

        return account.getBalance();
    }

    public BigDecimal deposit(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        Optional<Account> opt = accountRepository.findByAccountNumber(accountNumber);
        if (opt.isEmpty() || !opt.get().isActive()) {
            throw new IllegalArgumentException("Account not found or inactive");
        }
        Account account = opt.get();
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setType(Transaction.TransactionType.DEPOSIT);
        tx.setAmount(amount);
        tx.setBalanceAfter(account.getBalance());
        tx.setTimestamp(LocalDateTime.now());
        transactionRepository.save(tx);

        return account.getBalance();
    }

    public WithdrawResult withdraw(String accountNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        Optional<Account> opt = accountRepository.findByAccountNumber(accountNumber);
        if (opt.isEmpty() || !opt.get().isActive()) {
            throw new IllegalArgumentException("Account not found or inactive");
        }
        Account account = opt.get();

        if (amount.compareTo(account.getBalance()) > 0) {
            return new WithdrawResult(false, "Insufficient balance", null);
        }
        if (amount.compareTo(MAX_WITHDRAWAL_PER_TRANSACTION) > 0) {
            return new WithdrawResult(false,
                    "Amount exceeds max withdrawal per transaction (" + MAX_WITHDRAWAL_PER_TRANSACTION + ")",
                    null);
        }

        // Simple daily limit check (last 24h)
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);
        BigDecimal withdrawnToday = transactionRepository.findByAccount(account)
                .stream()
                .filter(t -> t.getType() == Transaction.TransactionType.WITHDRAWAL
                        && t.getTimestamp().isAfter(oneDayAgo))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (withdrawnToday.add(amount).compareTo(MAX_WITHDRAWAL_PER_DAY) > 0) {
            return new WithdrawResult(false,
                    "Amount exceeds daily withdrawal limit (" + MAX_WITHDRAWAL_PER_DAY + ")",
                    null);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setType(Transaction.TransactionType.WITHDRAWAL);
        tx.setAmount(amount);
        tx.setBalanceAfter(account.getBalance());
        tx.setTimestamp(LocalDateTime.now());
        transactionRepository.save(tx);

        // Simulate ATM cash denominations for the withdrawn amount
        DenominationBreakdown breakdown = computeDenominationBreakdown(amount);

        return new WithdrawResult(true, "Success", breakdown);
    }

    public static class DenominationBreakdown {
        public int hundreds;
        public int fifties;
        public int twenties;
        public int tens;
        public int fives;
        public int ones;

        public DenominationBreakdown(int hundreds, int fifties, int twenties,
                                      int tens, int fives, int ones) {
            this.hundreds = hundreds;
            this.fifties = fifties;
            this.twenties = twenties;
            this.tens = tens;
            this.fives = fives;
            this.ones = ones;
        }
    }

    // NOTE: this class was referenced by withdraw() and the controller in your
    // pasted code but never actually defined anywhere — that's the missing
    // piece that would have stopped this project from compiling.
    public static class WithdrawResult {
        public boolean success;
        public String message;
        public DenominationBreakdown breakdown;

        public WithdrawResult(boolean success, String message, DenominationBreakdown breakdown) {
            this.success = success;
            this.message = message;
            this.breakdown = breakdown;
        }
    }

    private DenominationBreakdown computeDenominationBreakdown(BigDecimal amount) {
        int remaining = amount.intValue(); // assume whole dollars for simplicity
        int hundreds = remaining / 100;
        remaining %= 100;
        int fifties = remaining / 50;
        remaining %= 50;
        int twenties = remaining / 20;
        remaining %= 20;
        int tens = remaining / 10;
        remaining %= 10;
        int fives = remaining / 5;
        remaining %= 5;
        int ones = remaining;
        return new DenominationBreakdown(hundreds, fifties, twenties, tens, fives, ones);
    }

    public List<Transaction> getTransactionHistory(String accountNumber, int limit) {
        Optional<Account> opt = accountRepository.findByAccountNumber(accountNumber);
        if (opt.isEmpty()) {
            return List.of();
        }
        Account account = opt.get();
        List<Transaction> all = transactionRepository.findByAccountOrderByTimestampDesc(account);
        if (limit <= 0 || limit >= all.size()) {
            return all;
        }
        return all.subList(0, limit);
    }

    public List<Transaction> getAllTransactionHistory(String accountNumber) {
        Optional<Account> opt = accountRepository.findByAccountNumber(accountNumber);
        if (opt.isEmpty()) {
            return List.of();
        }
        Account account = opt.get();
        return transactionRepository.findByAccountOrderByTimestampDesc(account);
    }

    public boolean closeAccount(String accountNumber) {
        Optional<Account> opt = accountRepository.findByAccountNumber(accountNumber);
        if (opt.isEmpty()) {
            return false;
        }
        Account account = opt.get();
        if (!account.isActive()) {
            return false;
        }
        account.setActive(false);

        Transaction tx = new Transaction();
        tx.setAccount(account);
        tx.setType(Transaction.TransactionType.ACCOUNT_CLOSURE);
        tx.setAmount(BigDecimal.ZERO);
        tx.setBalanceAfter(account.getBalance());
        tx.setTimestamp(LocalDateTime.now());
        transactionRepository.save(tx);

        accountRepository.save(account);
        return true;
    }
}
