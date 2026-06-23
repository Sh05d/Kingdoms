package com.kingdom.DTO.OUT;

import com.kingdom.Enums.ConnectedProvider;
import com.kingdom.Model.ConnectedAccount;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Clean response shape for a ConnectedAccount — provider + status only. Never exposes the OAuth tokens, and
 * leaves out the raw external account id + the token expiry (backend details a player doesn't need).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConnectedAccountOut {

    private Integer id;
    private ConnectedProvider provider;
    private String status;
    private LocalDateTime connectedAt;

    /** Map one ConnectedAccount entity to its clean (token-free) output shape. */
    public static ConnectedAccountOut from(ConnectedAccount a) {
        ConnectedAccountOut out = new ConnectedAccountOut();
        out.id = a.getId();
        out.provider = a.getProvider();
        out.status = a.getStatus();
        out.connectedAt = a.getConnectedAt();
        return out;
    }

    /** Map a list of ConnectedAccount entities to clean output shapes. */
    public static List<ConnectedAccountOut> fromList(List<ConnectedAccount> accounts) {
        List<ConnectedAccountOut> list = new ArrayList<>();
        for (ConnectedAccount a : accounts) {
            list.add(from(a));
        }
        return list;
    }
}
