package com.kingdom.Controller;

import com.kingdom.API.ApiException;
import com.kingdom.API.ApiResponse;
import com.kingdom.Config.CustomUserDetails;
import com.kingdom.DTO.IN.UserIn;
import com.kingdom.Model.User;
import com.kingdom.Repository.UserRepository;
import com.kingdom.Service.OtpService;
import com.kingdom.Service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Phone verification (OTP): send a code over WhatsApp, then verify it — a welcome email is sent on success.
 * Register is public (no account yet). Resend + verify identify the user from the HTTP Basic login (the account
 * already exists after register), so the phone is NEVER passed in the request — it comes from the authenticated
 * user, consistent with the rest of the API.
 * INTERIM piece in the User-flow domain (Maysun owns register/auth); self-contained so it merges cleanly.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final OtpService otpService;
    private final RegistrationService registrationService;
    private final UserRepository userRepository;

    /**
     * Create an account (User + Player + memberships) and immediately send the verification OTP over WhatsApp.
     * INTERIM (Maysun owns the final register); folds into her flow at the merge.
     */
    @PostMapping("/register")
    public Object register(@Valid @RequestBody UserIn request) {
        registrationService.register(request);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", "تم إنشاء حسابك بنجاح 👑 — أرسلنا رمز التحقق إلى رقمك عبر واتساب.");
        out.put("nextStep", "أدخل الرمز الذي وصلك على واتساب لتأكيد رقمك وتفعيل حسابك.");
        return out;
    }

    /** Resend the verification code to the LOGGED-IN user's phone (identity from HTTP Basic — no phone param). */
    @PostMapping("/send-otp")
    public ApiResponse sendOtp(@AuthenticationPrincipal CustomUserDetails me) {
        otpService.sendOtp(currentPhone(me));
        return new ApiResponse("أعدنا إرسال رمز التحقق إلى واتساب.");
    }

    /** Verify the code for the LOGGED-IN user; on success the welcome email is sent (identity from HTTP Basic). */
    @PostMapping("/verify-otp")
    public ApiResponse verifyOtp(@AuthenticationPrincipal CustomUserDetails me, @RequestParam String code) {
        otpService.verifyOtp(currentPhone(me), code);
        return new ApiResponse("تم تأكيد رقمك بنجاح ✅ — وأرسلنا لك رسالة ترحيب على بريدك الإلكتروني.");
    }

    /** The authenticated caller's phone — from their login, not the request. */
    private String currentPhone(CustomUserDetails me) {
        if (me == null) {
            throw new ApiException("سجّل الدخول بحسابك أولاً لتأكيد رقمك.");
        }
        User user = userRepository.findById(me.getId())
                .orElseThrow(() -> new ApiException("لم يتم العثور على المستخدم."));
        return user.getPhoneNumber();
    }
}
