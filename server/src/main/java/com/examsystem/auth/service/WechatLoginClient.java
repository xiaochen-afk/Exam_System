package com.examsystem.auth.service;

import com.examsystem.common.error.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 兑换微信一次性登录 code；只把用于关联的 OpenID 交给内部服务。 */
@Component
public class WechatLoginClient {
    private static final String SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final Pattern OPEN_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json;
    private final String appId;
    private final String appSecret;

    public WechatLoginClient(
            ObjectMapper json,
            @Value("${app.wechat.app-id:}") String appId,
            @Value("${app.wechat.app-secret:}") String appSecret) {
        this.json = json;
        this.appId = appId.strip();
        this.appSecret = appSecret.strip();
    }

    public String appId() {
        return appId;
    }

    public boolean isConfigured() {
        return appId.matches("[A-Za-z0-9]{1,64}") && !appSecret.isEmpty();
    }

    /** 固定 HTTPS 上游地址，禁用重定向；上游错误正文、请求地址及会话密钥不会进入日志。 */
    public byte[] exchange(String code) {
        if (code == null || code.isBlank() || code.length() > 256) {
            throw ApiException.invalid("请重新获取微信登录凭证");
        }
        if (!isConfigured()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_NOT_CONFIGURED", "微信登录暂未开放");
        }
        String query =
                "?appid="
                        + encode(appId)
                        + "&secret="
                        + encode(appSecret)
                        + "&js_code="
                        + encode(code)
                        + "&grant_type=authorization_code";
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(SESSION_URL + query))
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
        try {
            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw unavailable();
            }
            return readIdentity(json.readTree(response.body()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | JacksonException failure) {
            throw unavailable();
        }
    }

    private static byte[] readIdentity(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw unavailable();
        }
        JsonNode errorCode = body.path("errcode");
        int code = errorCode.isMissingNode() ? 0 : errorCode.intValue(-1);
        if (code == 40029 || code == 40163) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WECHAT_CODE_INVALID", "微信登录凭证已失效，请重试");
        }
        if (code == 45011) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "操作过于频繁，请稍后再试");
        }
        JsonNode value = body.path("openid");
        if (code != 0 || !value.isString()) {
            throw unavailable();
        }
        String openId = value.asString();
        if (!OPEN_ID.matcher(openId).matches()) {
            throw unavailable();
        }
        return openId.getBytes(StandardCharsets.US_ASCII);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "WECHAT_UNAVAILABLE", "微信登录服务暂时不可用");
    }
}
