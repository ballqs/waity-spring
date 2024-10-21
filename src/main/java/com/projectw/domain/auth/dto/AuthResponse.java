package com.projectw.domain.auth.dto;

import com.projectw.domain.auth.dto.AuthResponse.DuplicateCheck;
import com.projectw.domain.auth.dto.AuthResponse.Reissue;
import com.projectw.domain.auth.dto.AuthResponse.Login;
import com.projectw.domain.auth.dto.AuthResponse.Signup;
import com.projectw.domain.user.entitiy.User;

public sealed interface AuthResponse permits Signup, Login, Reissue, DuplicateCheck {

    record Signup(Long userId) implements AuthResponse { }

    record Login(Long id, String userId, String userNickname, String accessToken,
                 String refreshToken) implements AuthResponse {
        public Login(User user, String access, String refresh) {
            this(user.getId(), user.getUsername(), user.getNickname(), access, refresh);
        }
    }

    record Reissue(String accessToken, String refreshToken) implements AuthResponse {}
    record DuplicateCheck(boolean isDuplicated) implements AuthResponse {}
}