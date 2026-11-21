package com.examsystem.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** ControllerAdvice 与安全过滤链共用的错误协议，统一请求编号和 JSON/no-store 响应。 */
@Component
public class ApiErrors {
    private final ObjectMapper mapper;

    public ApiErrors(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Body(String code, String message, String requestId) {}

    /** 在服务端生成并放入请求属性，不直接信任客户端传来的编号或日志内容。 */
    public static String requestId(HttpServletRequest request) {
        Object current = request.getAttribute("requestId");
        if (current != null) {
            return current.toString();
        }
        String id = UUID.randomUUID().toString();
        request.setAttribute("requestId", id);
        return id;
    }

    public Body body(ApiException error, HttpServletRequest request) {
        return new Body(error.code(), error.getMessage(), requestId(request));
    }

    /** 安全过滤链无法经过 MVC 异常处理时，直接输出相同结构的错误响应。 */
    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter()
                .write(mapper.writeValueAsString(new Body(code, message, requestId(request))));
    }
}
