package com.kingdom.Service;

import com.kingdom.API.ApiException;
import com.kingdom.Model.User;
import com.kingdom.Repository.UserRepository;
import com.kingdom.Service.APIService.EmailService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal phone-verification (OTP) flow: send a 6-digit code over WhatsApp, verify it, and send a welcome
 * email on success (the feature Anas asked for).
 *
 * INTERIM piece in the User-flow domain (Maysun owns register + auth). It is intentionally self-contained:
 *  - codes live IN MEMORY (not the DB) — they are short-lived secrets, so no plaintext code is persisted;
 *  - on a successful verify it sets User.phoneVerified = true — the gate VerificationInterceptor enforces
 *    (an unverified player is blocked from every non-/auth route).
 * When Maysun's register/auth flow lands, fold this in (or replace it) and move the sendWelcome call there.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private final WhatsAppService whatsAppService;   // sends the code over WhatsApp
    private final EmailService emailService;         // welcome email on a successful verify
    private final UserRepository userRepository;     // resolve the user (for their email) by phone

    private static final long EXPIRY_MS = 5 * 60 * 1000;     // codes are valid for 5 minutes
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final Map<String, Code> codesByPhone = new ConcurrentHashMap<>();
    private final Set<String> alreadyWelcomed = ConcurrentHashMap.newKeySet(); // don't re-welcome the same phone

    private record Code(String value, long expiresAt) {}

    /** Generate a 6-digit code for this phone, store it, and send it over WhatsApp (needs Twilio enabled). */
    public void sendOtp(String phone) {
        String key = key(phone);
        if (key.isEmpty()) {
            throw new ApiException("Phone number is required");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codesByPhone.put(key, new Code(code, System.currentTimeMillis() + EXPIRY_MS));
        boolean sent = whatsAppService.sendOtp(phone, code);
        // DEV ONLY: surface the code in the server log so the flow can be tested without reading the WhatsApp
        // message (and so it works even when Twilio is off). REMOVE / gate behind a dev flag before production.
        log.info("[DEV-OTP] {} -> code {} (whatsapp sent={})", phone, code, sent);
    }

    /** Check the code; on success consume it and (once per phone) send the welcome email. */
    public void verifyOtp(String phone, String code) {
        String key = key(phone);
        Code stored = codesByPhone.get(key);
        if (stored == null) {
            throw new ApiException("No code was requested for this number — send one first");
        }
        if (System.currentTimeMillis() > stored.expiresAt()) {
            codesByPhone.remove(key);
            throw new ApiException("Code expired — request a new one");
        }
        if (!stored.value().equals(code == null ? "" : code.trim())) {
            throw new ApiException("Incorrect code");
        }
        codesByPhone.remove(key); // one-time use

        User user = userRepository.findUserByPhoneNumber(key); // normalized phone, matches the stored E.164

        // THE GATE: mark this account's phone verified so VerificationInterceptor lets the player act.
        if (user != null && !Boolean.TRUE.equals(user.getPhoneVerified())) {
            user.setPhoneVerified(true);
            userRepository.save(user);
        }

        // Welcome email — once per phone, best-effort (EmailService no-ops if Mailtrap is off/unconfigured).
        if (user != null && alreadyWelcomed.add(key)) {
            emailService.sendWelcome(user.getEmail(), user.getUsername());
        }
    }

    // Canonical key for matching send<->verify. Normalize to E.164 (+9665..) so the code is found whatever
    // format arrives — including a query param where Spring decodes the URL '+' to a space (" 9665.." -> "+9665..").
    private String key(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        return whatsAppService.normalizeToE164(phone);
    }
}
