package com.example.atmsimulation.controller;

import com.example.atmsimulation.model.Account;
import com.example.atmsimulation.model.Transaction;
import com.example.atmsimulation.service.AtmService;
import com.example.atmsimulation.service.AtmService.WithdrawResult;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/")
public class AtmController {

    private final AtmService atmService;

    public AtmController(AtmService atmService) {
        this.atmService = atmService;
    }

    @GetMapping
    public String home() {
        return "redirect:/atm/start";
    }

    @GetMapping("/atm/start")
    public String start() {
        return "atm/start";
    }

    @PostMapping("/atm/insert-card")
    public String insertCard(@RequestParam String accountNumber, Model model) {
        Optional<Account> opt = atmService.getAccountByNumber(accountNumber);
        if (opt.isEmpty() || !opt.get().isActive()) {
            model.addAttribute("error", "Account not found or inactive.");
            return "atm/start";
        }
        model.addAttribute("accountNumber", accountNumber);
        return "atm/pin";
    }

    @PostMapping("/atm/verify-pin")
    public String verifyPin(@RequestParam String accountNumber,
                             @RequestParam String pin,
                             Model model) {
        if (atmService.isAccountLocked(accountNumber)) {
            model.addAttribute("error", "Account is locked due to too many failed attempts.");
            return "atm/start";
        }
        if (!atmService.verifyPin(accountNumber, pin)) {
            model.addAttribute("accountNumber", accountNumber);
            model.addAttribute("error", "Incorrect PIN. Try again.");
            return "atm/pin";
        }
        Account account = atmService.getAccountByNumber(accountNumber).get();
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("name", account.getName());
        return "atm/menu";
    }

    @GetMapping("/atm/menu")
    public String menu(@RequestParam String accountNumber,
                        @RequestParam String name,
                        Model model) {
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("name", name);
        return "atm/menu";
    }

    @GetMapping("/atm/balance")
    public String balance(@RequestParam String accountNumber,
                           @RequestParam String name,
                           Model model) {
        BigDecimal balance = atmService.getBalance(accountNumber);
        if (balance == null) {
            model.addAttribute("error", "Account not found or inactive.");
            return "atm/start";
        }
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("name", name);
        model.addAttribute("balance", balance);
        return "atm/balance";
    }

    @GetMapping("/atm/deposit")
    public String depositForm(@RequestParam String accountNumber,
                               @RequestParam String name,
                               Model model) {
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("name", name);
        return "atm/deposit";
    }

    @PostMapping("/atm/deposit")
    public String depositSubmit(@RequestParam String accountNumber,
                                 @RequestParam String name,
                                 @RequestParam BigDecimal amount,
                                 Model model) {
        try {
            BigDecimal newBalance = atmService.deposit(accountNumber, amount);
            model.addAttribute("accountNumber", accountNumber);
            model.addAttribute("name", name);
            model.addAttribute("amount", amount);
            model.addAttribute("newBalance", newBalance);
            return "atm/deposit-success";
        } catch (IllegalArgumentException e) {
            model.addAttribute("accountNumber", accountNumber);
            model.addAttribute("name", name);
            model.addAttribute("error", e.getMessage());
            return "atm/deposit";
        }
    }

    @GetMapping("/atm/withdraw")
    public String withdrawForm(@RequestParam String accountNumber,
                                @RequestParam String name,
                                Model model) {
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("name", name);
        return "atm/withdraw";
    }

    @PostMapping("/atm/withdraw")
    public String withdrawSubmit(@RequestParam String accountNumber,
                                  @RequestParam String name,
                                  @RequestParam BigDecimal amount,
                                  Model model) {
        try {
            WithdrawResult result = atmService.withdraw(accountNumber, amount);
            if (!result.success) {
                model.addAttribute("accountNumber", accountNumber);
                model.addAttribute("name", name);
                model.addAttribute("error", result.message);
                return "atm/withdraw";
            }
            model.addAttribute("accountNumber", accountNumber);
            model.addAttribute("name", name);
            model.addAttribute("amount", amount);
            model.addAttribute("breakdown", result.breakdown);
            return "atm/withdraw-success";
        } catch (IllegalArgumentException e) {
            model.addAttribute("accountNumber", accountNumber);
            model.addAttribute("name", name);
            model.addAttribute("error", e.getMessage());
            return "atm/withdraw";
        }
    }

    @GetMapping("/atm/history")
    public String history(@RequestParam String accountNumber,
                           @RequestParam String name,
                           @RequestParam(defaultValue = "10") int limit,
                           Model model) {
        List<Transaction> transactions = atmService.getTransactionHistory(accountNumber, limit);
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("name", name);
        model.addAttribute("transactions", transactions);
        model.addAttribute("limit", limit);

        // Build simple chart data for Chart.js: oldest -> newest, left to right.
        // Withdrawals are shown as negative bars so the chart reads like a
        // cash-flow graph (up = money in, down = money out).
        List<Transaction> chronological = new ArrayList<>(transactions);
        Collections.reverse(chronological);

        DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("MM/dd HH:mm");
        StringBuilder labels = new StringBuilder();
        StringBuilder amounts = new StringBuilder();
        for (Transaction t : chronological) {
            if (labels.length() > 0) {
                labels.append(",");
                amounts.append(",");
            }
            labels.append("'").append(t.getTimestamp().format(labelFormat)).append("'");
            BigDecimal signedAmount = t.getType() == Transaction.TransactionType.WITHDRAWAL
                    ? t.getAmount().negate() : t.getAmount();
            amounts.append(signedAmount);
        }
        model.addAttribute("chartLabels", labels.toString());
        model.addAttribute("chartAmounts", amounts.toString());

        return "atm/history";
    }

    @GetMapping("/atm/change-pin")
    public String changePinForm(@RequestParam String accountNumber,
                                 @RequestParam String name,
                                 Model model) {
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("name", name);
        return "atm/change-pin";
    }

    @PostMapping("/atm/change-pin")
    public String changePinSubmit(@RequestParam String accountNumber,
                                   @RequestParam String oldPin,
                                   @RequestParam String newPin,
                                   @RequestParam String confirmNewPin,
                                   Model model) {
        // Look the account up ourselves rather than trusting a hidden form
        // field for "name" — this is also what fixes the undefined-variable
        // bug that was in the original version of this method.
        String name = atmService.getAccountByNumber(accountNumber)
                .map(Account::getName)
                .orElse("");

        if (!newPin.equals(confirmNewPin)) {
            model.addAttribute("accountNumber", accountNumber);
            model.addAttribute("name", name);
            model.addAttribute("error", "New PIN and confirmation do not match.");
            return "atm/change-pin";
        }
        boolean ok = atmService.changePin(accountNumber, oldPin, newPin);
        if (!ok) {
            model.addAttribute("accountNumber", accountNumber);
            model.addAttribute("name", name);
            model.addAttribute("error", "Old PIN is incorrect or account is inactive.");
            return "atm/change-pin";
        }
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("name", name);
        return "atm/change-pin-success";
    }

    @GetMapping("/atm/close-account")
    public String closeAccountForm(@RequestParam String accountNumber,
                                    @RequestParam String name,
                                    Model model) {
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("name", name);
        Account account = atmService.getAccountByNumber(accountNumber).get();
        model.addAttribute("balance", account.getBalance());
        return "atm/close-account";
    }

    @PostMapping("/atm/close-account")
    public String closeAccountSubmit(@RequestParam String accountNumber,
                                      @RequestParam String confirm,
                                      Model model) {
        if (!"YES".equalsIgnoreCase(confirm)) {
            Account account = atmService.getAccountByNumber(accountNumber).get();
            model.addAttribute("error", "You must confirm with 'YES'.");
            model.addAttribute("accountNumber", accountNumber);
            model.addAttribute("name", account.getName());
            model.addAttribute("balance", account.getBalance());
            return "atm/close-account";
        }
        atmService.closeAccount(accountNumber);
        return "atm/close-account-success";
    }

    @GetMapping("/atm/pdf-statement")
    public void pdfStatement(@RequestParam String accountNumber,
                              HttpServletResponse response) throws IOException {
        Account account = atmService.getAccountByNumber(accountNumber).orElse(null);
        if (account == null || !account.isActive()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Account not found");
            return;
        }

        List<Transaction> transactions = atmService.getAllTransactionHistory(accountNumber);

        response.setContentType("application/pdf");
        String filename = "statement_" + accountNumber + ".pdf";
        response.setHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");

        Document document = new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(document, response.getOutputStream());
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 12);

            Paragraph title = new Paragraph("ATM Statement", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            document.add(new Paragraph(" "));

            document.add(new Paragraph("Account Number: " + account.getAccountNumber(), normalFont));
            document.add(new Paragraph("Name: " + account.getName(), normalFont));
            document.add(new Paragraph("Balance: " + account.getBalance(), normalFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.addCell("Date & Time");
            table.addCell("Type");
            table.addCell("Amount");
            table.addCell("Balance After");
            table.addCell("ID");

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            for (Transaction t : transactions) {
                table.addCell(t.getTimestamp().format(formatter));
                table.addCell(t.getType().name());
                table.addCell(t.getAmount().toString());
                table.addCell(t.getBalanceAfter().toString());
                table.addCell(String.valueOf(t.getId()));
            }

            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Generated on: " + LocalDateTime.now().format(formatter), normalFont));
        } catch (DocumentException e) {
            throw new RuntimeException(e);
        } finally {
            document.close();
        }
    }

    @GetMapping("/atm/create-account")
    public String createAccountForm(Model model) {
        return "atm/create-account";
    }

    @PostMapping("/atm/create-account")
    public String createAccountSubmit(@RequestParam String name,
                                       @RequestParam String accountNumber,
                                       @RequestParam String pin,
                                       @RequestParam String confirmPin,
                                       @RequestParam(required = false) BigDecimal initialBalance,
                                       Model model) {
        if (!pin.equals(confirmPin)) {
            model.addAttribute("error", "PIN and confirmation do not match.");
            return "atm/create-account";
        }
        try {
            Account account = atmService.createAccount(name, accountNumber, pin,
                    initialBalance != null ? initialBalance : BigDecimal.ZERO);
            model.addAttribute("accountNumber", account.getAccountNumber());
            model.addAttribute("name", account.getName());
            model.addAttribute("balance", account.getBalance());
            return "atm/create-account-success";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "atm/create-account";
        }
    }

    @GetMapping("/atm/view-account")
    public String viewAccountForm(Model model) {
        return "atm/view-account";
    }

    @PostMapping("/atm/view-account")
    public String viewAccountSubmit(@RequestParam String accountNumber,
                                     Model model) {
        Optional<Account> opt = atmService.getAccountByNumber(accountNumber);
        if (opt.isEmpty()) {
            model.addAttribute("error", "Account not found.");
            return "atm/view-account";
        }
        Account account = opt.get();
        model.addAttribute("accountNumber", account.getAccountNumber());
        model.addAttribute("name", account.getName());
        model.addAttribute("balance", account.getBalance());
        model.addAttribute("active", account.isActive());
        model.addAttribute("createdAt", account.getCreatedAt());
        return "atm/view-account-result";
    }
}
