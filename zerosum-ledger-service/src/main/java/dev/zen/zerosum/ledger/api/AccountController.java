package dev.zen.zerosum.ledger.api;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.dto.request.CreateAccountRequest;
import dev.zen.zerosum.ledger.dto.response.AccountResponse;
import dev.zen.zerosum.ledger.dto.response.BalanceResponse;
import dev.zen.zerosum.ledger.service.AccountService;
import dev.zen.zerosum.ledger.service.BalanceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final BalanceService balanceService;

    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @Valid @RequestBody CreateAccountRequest request) {
        Account account =
                accountService.open(
                        request.code(),
                        request.name(),
                        Currencies.parse(request.currency()),
                        request.type());
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(account.getId())
                        .toUri();
        return ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return AccountResponse.from(accountService.get(id));
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable UUID id) {
        return BalanceResponse.from(id, balanceService.balanceOf(id));
    }
}
