package com.example.learning.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 认证相关请求体。所有字段均做服务端校验，前端校验仅用于体验。
 */
public final class AuthRequests {

    private AuthRequests() {
    }

    public record PasswordLogin(
            @NotBlank(message = "请输入账号") String account,
            @NotBlank(message = "请输入密码") String password,
            String captchaId,
            String captchaCode,
            List<String> activeRoleCodes) {
    }

    public record OtpLogin(
            @NotBlank(message = "请选择验证码渠道") String channel,
            @NotBlank(message = "请输入邮箱或手机号") String target,
            @NotBlank(message = "请输入验证码") String code,
            List<String> activeRoleCodes) {
    }

    public record SendCode(
            @NotBlank(message = "缺少验证码场景") String scene,
            @NotBlank(message = "请选择验证码渠道") String channel,
            @NotBlank(message = "请输入邮箱或手机号") String target) {
    }

    public record Register(
            @NotBlank(message = "请输入用户名")
            @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{3,31}$",
                    message = "用户名需以字母开头，4-32 位字母/数字/下划线")
            String username,
            @NotBlank(message = "请输入密码")
            @Size(min = 10, max = 72, message = "密码长度需为 10-72 位")
            String password,
            @NotBlank(message = "请再次输入密码") String confirmPassword,
            @Size(max = 64, message = "昵称不能超过 64 字") String displayName,
            @NotBlank(message = "请输入邮箱")
            @Email(message = "邮箱格式不正确")
            @Size(max = 128, message = "邮箱过长")
            String email,
            @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
            String phone,
            @NotBlank(message = "请输入邮箱验证码") String emailCode,
            String captchaId,
            String captchaCode) {
    }

    public record Forgot(
            @NotBlank(message = "请输入账号") String account,
            String captchaId,
            String captchaCode) {
    }

    public record Reset(
            @NotBlank(message = "请输入账号") String account,
            @NotBlank(message = "请输入验证码") String code,
            @NotBlank(message = "请输入新密码")
            @Size(min = 10, max = 72, message = "密码长度需为 10-72 位")
            String newPassword,
            @NotBlank(message = "请再次输入新密码") String confirmPassword) {
    }

    public record ChangePassword(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码")
            @Size(min = 10, max = 72, message = "密码长度需为 10-72 位")
            String newPassword,
            @NotBlank(message = "请再次输入新密码") String confirmPassword) {
    }

    public record SwitchRoles(
            @NotEmpty(message = "请至少选择一个角色") List<String> roleCodes) {
    }

    public record SsoToken(
            @NotBlank(message = "缺少授权码") String code,
            @NotBlank(message = "缺少 client_id") String clientId,
            @NotBlank(message = "缺少 redirect_uri") String redirectUri,
            String codeVerifier,
            String clientSecret) {
    }
}
