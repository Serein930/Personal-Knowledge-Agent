package com.agentmind.common.security;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 当前用户编号参数解析器。
 *
 * <p>本地 JWT 和 OIDC 令牌都优先读取 {@code uid} 声明；OIDC 提供方也可以把数字用户编号
 * 放在 {@code sub} 中。关闭安全模式仅用于测试和本地旧接口联调，固定返回种子用户 1。</p>
 */
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    private final AgentMindSecurityProperties properties;

    public CurrentUserIdArgumentResolver(AgentMindSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && Long.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (properties.getMode() == SecurityMode.DISABLED
                && !(authentication instanceof JwtAuthenticationToken)) {
            return 1L;
        }
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "当前请求缺少有效身份令牌");
        }
        Object uid = jwtAuthentication.getToken().getClaims().get("uid");
        String value = uid == null ? jwtAuthentication.getToken().getSubject() : uid.toString();
        try {
            long userId = Long.parseLong(value);
            if (userId <= 0) {
                throw new NumberFormatException("用户编号不是正数");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "身份令牌缺少有效的用户编号声明");
        }
    }
}
