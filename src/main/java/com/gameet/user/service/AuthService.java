package com.gameet.user.service;

import com.gameet.common.enums.EmailPurpose;
import com.gameet.global.config.websocket.manager.WebSocketSessionCoordinator;
import com.gameet.global.exception.JwtAuthenticationException;
import com.gameet.notification.dto.TemplatedEmailRequest;
import com.gameet.notification.enums.AwsSesTemplateType;
import com.gameet.notification.service.AwsSesEmailNotifier;
import com.gameet.user.dto.request.LoginRequest;
import com.gameet.user.dto.request.SignUpRequest;
import com.gameet.user.enums.Role;
import com.gameet.global.jwt.JwtUtil;
import com.gameet.user.repository.EmailVerificationCodeRepository;
import com.gameet.user.repository.PasswordResetTokenRepository;
import com.gameet.user.repository.RefreshTokenRepository;
import com.gameet.global.exception.CustomException;
import com.gameet.global.exception.ErrorCode;
import com.gameet.user.dto.response.UserResponse;
import com.gameet.user.entity.User;
import com.gameet.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.server.Cookie;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    public static final String HEADER_PASSWORD_RESET_TOKEN = "Password-Reset-Token";

    private final JwtUtil jwtUtil;
    private final AwsSesEmailNotifier emailNotifier;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final WebSocketSessionCoordinator webSocketSessionCoordinator;

    @Transactional
    public UserResponse registerUser(SignUpRequest signUpRequest, Role role, HttpServletResponse httpServletResponse) {
        if (userRepository.existsByEmail(signUpRequest.email())) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }

        try {
            User user = User.of(signUpRequest, role);
            userRepository.save(user);

            issueTokenAndAttachToResponse(user.getUserId(), user.getRole(), false, httpServletResponse);
            return UserResponse.of(user);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    @Transactional
    public UserResponse login(LoginRequest loginRequest, HttpServletResponse httpServletResponse) {
        User user = userRepository.findByEmail(loginRequest.email())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND_USER));

        user.verifyPasswordMatching(loginRequest.password());

        issueTokenAndAttachToResponse(user.getUserId(), user.getRole(), loginRequest.rememberMe(), httpServletResponse);

        return UserResponse.of(user);
    }

    public void issueTokenAndAttachToResponse(Long userId, Role role, Boolean rememberMe, HttpServletResponse httpServletResponse) {
        String accessToken = jwtUtil.generateAccessToken(userId, role);
        httpServletResponse.setHeader(JwtUtil.HEADER_AUTHORIZATION, JwtUtil.TOKEN_PREFIX + accessToken);

        if (rememberMe != null && rememberMe) {
            generateRefreshToken(userId, role, httpServletResponse);
        }
    }

    public void issueTokenAndAttachToResponse(Long userId, Role role, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String accessToken = jwtUtil.generateAccessToken(userId, role);
        httpServletResponse.setHeader(JwtUtil.HEADER_AUTHORIZATION, JwtUtil.TOKEN_PREFIX + accessToken);


        String refreshToken = jwtUtil.getRefreshToken(httpServletRequest);
        if (refreshToken != null) {
            generateRefreshToken(userId, role, httpServletResponse);
        }
    }

    private void generateRefreshToken(Long userId, Role role, HttpServletResponse httpServletResponse) {
        String refreshToken = jwtUtil.generateRefreshToken(userId, role);

        ResponseCookie cookie = ResponseCookie.from(JwtUtil.COOKIE_REFRESH_TOKEN_NAME, refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(60 * 60 * 24 * 7)
                .sameSite(Cookie.SameSite.NONE.attributeValue())
                .build();

        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        refreshTokenRepository.saveRefreshToken(userId, refreshToken);
    }

    public void reissueAccessToken(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String refreshToken = jwtUtil.getRefreshToken(httpServletRequest);
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new JwtAuthenticationException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        Long userId = jwtUtil.getUserIdFromToken(refreshToken);
        Role role = jwtUtil.getRoleFromToken(refreshToken);

        boolean isValid = refreshTokenRepository.isValidRefreshToken(userId, refreshToken);
        if (!isValid) {
            throw new JwtAuthenticationException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        String newAccessToken = jwtUtil.generateAccessToken(userId, role);
        httpServletResponse.setHeader(JwtUtil.HEADER_AUTHORIZATION, JwtUtil.TOKEN_PREFIX + newAccessToken);
    }

    public void logout(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        String accessToken = jwtUtil.getAccessToken(httpServletRequest);
        Long userId = jwtUtil.getUserIdFromToken(accessToken);

        String refreshToken = jwtUtil.getRefreshToken(httpServletRequest);
        if (refreshToken != null) {
            refreshTokenRepository.deleteRefreshTokenByUserId(userId);
        }

        if (accessToken != null) {
            // TODO: BLACKLIST 추가
        }

        ResponseCookie cookie = ResponseCookie.from(JwtUtil.COOKIE_REFRESH_TOKEN_NAME, null)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(60 * 60 * 24 * 7)
                .sameSite(Cookie.SameSite.NONE.attributeValue())
                .build();

        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        webSocketSessionCoordinator.closeSessionsOnLogout(userId);
    }

    public void sendVerificationCode(String toEmail, EmailPurpose emailPurpose) {
        if (emailPurpose == EmailPurpose.PASSWORD_RESET) {
            if (!userRepository.existsByEmail(toEmail)) {
                throw new CustomException(ErrorCode.USER_NOT_FOUND_BY_EMAIL);
            }
        } else if (emailPurpose == EmailPurpose.SIGN_UP) {
            if (userRepository.existsByEmail(toEmail)) {
                throw new CustomException(ErrorCode.DUPLICATE_EMAIL);
            }
        } else {
            throw new CustomException(ErrorCode.INVALID_EMAIL_PURPOSE);
        }

        String verificationCode = generateRandomCode();
        try {
            saveVerificationCode(toEmail, verificationCode, emailPurpose);

            TemplatedEmailRequest templatedEmailRequest = TemplatedEmailRequest.builder()
                    .toEmail(toEmail)
                    .awsSesTemplateType(AwsSesTemplateType.EMAIL_VERIFICATION)
                    .templateData(Map.of(
                            "emailPurpose", emailPurpose.getDescription(),
                            "verificationCode", verificationCode))
                    .build();

            emailNotifier.sendTemplatedEmail(templatedEmailRequest);
        } catch (Exception e) {
            log.error("이메일 인증 코드 처리 실패", e);
            throw new CustomException(ErrorCode.EMAIL_SEND_FAIL);
        }
    }

    private String generateRandomCode() {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            int index = random.nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    private void saveVerificationCode(String email, String code, EmailPurpose emailPurpose) {
        emailVerificationCodeRepository.saveEmailVerificationCode(email, code, emailPurpose);
    }

    public void verifyVerificationCode(String email, String code, EmailPurpose emailPurpose,
                                       HttpServletResponse httpServletResponse) {
        Boolean isValid = emailVerificationCodeRepository.isValidEmailVerificationCode(email, code, emailPurpose);
        if (!isValid) {
            throw new CustomException(ErrorCode.EMAIL_VERIFICATION_FAILED);
        }
        else {
            emailVerificationCodeRepository.deleteEmailVerificationCode(email, emailPurpose);

            if (emailPurpose == EmailPurpose.PASSWORD_RESET) {
                String passwordResetToken = issuePasswordResetToken(email);
                httpServletResponse.setHeader(HEADER_PASSWORD_RESET_TOKEN, passwordResetToken);
            }
        }
    }

    private String issuePasswordResetToken(String email) {
        return passwordResetTokenRepository.issuePasswordResetToken(email);
    }

    public String issueWebsocketToken(Long userId, Role role) {
        return jwtUtil.generateWebSocketToken(userId, role);
    }
}
