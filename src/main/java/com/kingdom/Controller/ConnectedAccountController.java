package com.kingdom.Controller;

import com.kingdom.API.ApiResponse;
import com.kingdom.Config.CustomUserDetails;
import com.kingdom.DTO.IN.ConnectedAccountIn;
import com.kingdom.DTO.OUT.ConnectedAccountOut;
import com.kingdom.Enums.ConnectedProvider;
import com.kingdom.Service.ConnectedAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/connecte")
@RequiredArgsConstructor
public class ConnectedAccountController {
    private final ConnectedAccountService connectedAccountService;

    // Reads return ConnectedAccountOut (token-free) — never the raw entity, which would leak the OAuth tokens.

    @GetMapping("/get")
    public List<ConnectedAccountOut> getAllConnectedAccounts() {
        return ConnectedAccountOut.fromList(connectedAccountService.getAllConnectedAccounts());
    }

    @PostMapping("/add")
    public ApiResponse addConnectedAccount(@Valid @RequestBody ConnectedAccountIn account) {
        connectedAccountService.addConnectedAccount(account);
        return new ApiResponse("Connected account added successfully");
    }

    @PutMapping("/update/{id}")
    public ApiResponse updateConnectedAccount(@PathVariable Integer id, @Valid @RequestBody ConnectedAccountIn account) {
        connectedAccountService.updateConnectedAccount(id, account);
        return new ApiResponse("Connected account updated successfully");
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse deleteConnectedAccount(@PathVariable Integer id) {
        connectedAccountService.deleteConnectedAccount(id);
        return new ApiResponse("Connected account deleted successfully");
    }

    @GetMapping("/get/{id}")
    public ConnectedAccountOut getConnectedAccountById(@PathVariable Integer id) {
        return ConnectedAccountOut.from(connectedAccountService.getConnectedAccountById(id));
    }

    // ---- Flow endpoints ----

    @PostMapping("/link")
    public ApiResponse linkConnectedAccount(@AuthenticationPrincipal CustomUserDetails me, @Valid @RequestBody ConnectedAccountIn account) {
        connectedAccountService.linkAccount(me.getId(), account);
        return new ApiResponse("Connected account linked successfully");
    }

    @GetMapping("/player")
    public List<ConnectedAccountOut> getConnectedAccountsByPlayer(@AuthenticationPrincipal CustomUserDetails me) {
        return ConnectedAccountOut.fromList(connectedAccountService.getAccountsByPlayer(me.getId()));
    }

    @DeleteMapping("/disconnect/{provider}")
    public ApiResponse disconnectConnectedAccount(@AuthenticationPrincipal CustomUserDetails me, @PathVariable ConnectedProvider provider) {
        connectedAccountService.disconnect(me.getId(), provider);
        return new ApiResponse("Connected account disconnected successfully");
    }
}
