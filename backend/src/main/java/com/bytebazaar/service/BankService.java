package com.bytebazaar.service;

import com.bytebazaar.dto.Dtos.*;
import com.bytebazaar.entity.*;
import com.bytebazaar.exception.*;
import com.bytebazaar.repository.BankAccountRepository;
import com.bytebazaar.util.MaskUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bank accounts for users, sellers and the admin. Only masked numbers ever leave the backend. */
@Service
@RequiredArgsConstructor
@Transactional
public class BankService {
    private final BankAccountRepository repo;

    public static BankDto dto(BankAccount b) {
        return new BankDto(b.getId(), b.getHolderName(), b.getBankName(), MaskUtil.account(b.getAccountNumber()), b.getIfsc(),
                b.getUpiId(), b.isPrimaryAccount(), b.isDemo(), b.isActive());
    }

    @Transactional(readOnly = true)
    public List<BankDto> list(User u) { return repo.findByUserOrderByIdAsc(u).stream().map(BankService::dto).toList(); }

    public BankDto add(User u, BankReq r) {
        if (!r.accountNumber().equals(r.confirmAccountNumber())) throw new InvalidRequestException("Account numbers do not match");
        BankAccount b = new BankAccount();
        b.setUser(u);
        fill(b, r);
        boolean first = !repo.existsByUser(u);
        b.setPrimaryAccount(first || r.primary());
        if (b.isPrimaryAccount()) clearPrimary(u);
        return dto(repo.save(b));
    }

    public BankDto update(User u, Long id, BankReq r) {
        BankAccount b = repo.findByIdAndUser(id, u).orElseThrow(() -> new NotFoundException("Bank account not found"));
        if (b.isDemo() && u.getRole() != Role.ADMIN && u.getRole() != Role.SELLER) throw new ForbiddenException("Demo accounts cannot be edited");
        if (!r.accountNumber().equals(r.confirmAccountNumber())) throw new InvalidRequestException("Account numbers do not match");
        fill(b, r);
        if (r.primary()) { clearPrimary(u); b.setPrimaryAccount(true); }
        return dto(b);
    }

    public void delete(User u, Long id) {
        BankAccount b = repo.findByIdAndUser(id, u).orElseThrow(() -> new NotFoundException("Bank account not found"));
        boolean wasPrimary = b.isPrimaryAccount();
        repo.delete(b);
        repo.flush();
        if (wasPrimary) repo.findByUserOrderByIdAsc(u).stream().findFirst().ifPresent(x -> x.setPrimaryAccount(true));
    }

    public BankDto setPrimary(User u, Long id) {
        BankAccount b = repo.findByIdAndUser(id, u).orElseThrow(() -> new NotFoundException("Bank account not found"));
        clearPrimary(u);
        b.setPrimaryAccount(true);
        return dto(b);
    }

    public BankDto setActive(User u, Long id, boolean active) {
        BankAccount b = repo.findByIdAndUser(id, u).orElseThrow(() -> new NotFoundException("Bank account not found"));
        b.setActive(active);
        return dto(b);
    }

    private void clearPrimary(User u) { repo.findByUserOrderByIdAsc(u).forEach(x -> x.setPrimaryAccount(false)); }

    private void fill(BankAccount b, BankReq r) {
        b.setHolderName(r.holderName().trim()); b.setBankName(r.bankName().trim());
        b.setAccountNumber(r.accountNumber()); b.setIfsc(r.ifsc().toUpperCase()); b.setUpiId(r.upiId());
    }
}
