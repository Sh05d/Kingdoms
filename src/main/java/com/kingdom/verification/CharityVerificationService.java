package com.kingdom.verification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CHARITY verification: confirms a player actually donated by reading their bank transactions through
 * Neotek Open Banking ({@link NeotekClient}).
 *
 * Pass rule: among the consented PSU's transactions in [from, to], find an OUTGOING (KSAOB.Debit), BOOKED
 * payment whose amount >= the target and (if a charity name is given) whose payee/narrative matches it.
 *
 * DEMO HELPER: the Neotek sandbox account has no charity transactions, so the real flow always rejects.
 * {@link #recordDonation} lets you inject a SIMULATED donation (kept in memory) so the PASS path + XP can be
 * demonstrated. This is clearly a testing shortcut, separate from the real bank read.
 */
@Service
@RequiredArgsConstructor
public class CharityVerificationService {

    private final NeotekClient neotekClient;

    // Simulated donations for the demo (PSUId -> list). In-memory: resets on restart. Real flow ignores this.
    private final Map<String, List<Donation>> simulatedDonations = new ConcurrentHashMap<>();

    /** A simulated donation used only by the demo helper. */
    public record Donation(String charity, double amountSar, LocalDateTime when) {
    }

    /** DEMO HELPER: record a simulated donation for a PSU so the next charity check can pass. */
    public void recordDonation(String psuId, String charity, double amountSar) {
        simulatedDonations
                .computeIfAbsent(psuId, k -> new ArrayList<>())
                .add(new Donation(charity == null ? "" : charity, amountSar, LocalDateTime.now()));
    }

    /**
     * Did this PSU donate at least {@code minAmountSar} within [from, to]?
     * Checks the SIMULATED donations first (demo helper), then the real Neotek transactions.
     * Blank {@code charityName} => any qualifying outgoing payment counts; otherwise the payee name or
     * transaction narrative must contain it.
     * @return true only if a matching donation is found; false if Neotek is off / no match.
     */
    public boolean hasDonated(String psuId, String charityName, int minAmountSar,
                              LocalDateTime from, LocalDateTime to) {
        String needle = (charityName == null) ? "" : charityName.trim().toLowerCase();

        // 1) Simulated donations (demo). Lets the PASS path work even though the sandbox has no charity data.
        for (Donation donation : simulatedDonations.getOrDefault(psuId, List.of())) {
            if (donation.amountSar() < minAmountSar) {
                continue;
            }
            if (!inWindow(donation.when(), from, to)) {
                continue;
            }
            if (needle.isEmpty() || donation.charity().toLowerCase().contains(needle)) {
                return true;
            }
        }

        // 2) Real bank transactions from Neotek.
        JsonNode response = neotekClient.listTransactions(psuId);
        if (response == null) {
            return false;
        }
        JsonNode transactions = response.path("Data").path("Transactions");
        if (!transactions.isArray()) {
            return false;
        }
        for (JsonNode tx : transactions) {
            if (!"KSAOB.Debit".equals(tx.path("CreditDebitIndicator").asText(""))) {
                continue; // outgoing payments only (a donation leaves the account)
            }
            if (!"Booked".equalsIgnoreCase(tx.path("Status").asText(""))) {
                continue; // settled transactions only
            }
            // Debit amounts come back NEGATIVE (money leaving the account), so compare the magnitude.
            if (Math.abs(tx.path("Amount").path("Amount").asDouble(0)) < minAmountSar) {
                continue;
            }
            if (!inWindow(tx.path("BookingDateTime").asText(""), from, to)) {
                continue;
            }
            String payee = tx.path("MerchantDetails").path("MerchantName").asText("").toLowerCase();
            String narrative = tx.path("TransactionInformation").asText("").toLowerCase();
            if (needle.isEmpty() || payee.contains(needle) || narrative.contains(needle)) {
                return true; // found a matching donation
            }
        }
        return false;
    }

    // Is a LocalDateTime inside [from, to]? (used by the simulated donations)
    private boolean inWindow(LocalDateTime when, LocalDateTime from, LocalDateTime to) {
        if (when == null) {
            return true;
        }
        if (from != null && when.isBefore(from)) {
            return false;
        }
        if (to != null && when.isAfter(to)) {
            return false;
        }
        return true;
    }

    // Is the transaction's BookingDateTime inside [from, to]? Lenient: null window or unparseable date => keep it.
    private boolean inWindow(String bookingDateTime, LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            return true;
        }
        if (bookingDateTime == null || bookingDateTime.isBlank()) {
            return true;
        }
        LocalDateTime when = parseDateTime(bookingDateTime);
        if (when == null) {
            return true; // unparseable date -> don't exclude it
        }
        return inWindow(when, from, to);
    }

    // Parse an Open-Banking timestamp tolerantly: offset datetime, then plain datetime, then date-only.
    private LocalDateTime parseDateTime(String text) {
        try { return OffsetDateTime.parse(text).toLocalDateTime(); } catch (Exception ignored) { }
        try { return LocalDateTime.parse(text); } catch (Exception ignored) { }
        try { return LocalDate.parse(text).atStartOfDay(); } catch (Exception ignored) { }
        return null;
    }
}
