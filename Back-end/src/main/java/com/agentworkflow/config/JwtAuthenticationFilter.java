package com.agentworkflow.config;

import com.agentworkflow.utils.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT认证过滤器
 * 用于拦截请求，验证JWT令牌，并设置用户认证信息
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        logger.info("JWT过滤器被调用，请求URI: {}", request.getRequestURI());
        
        // 跳过注册和登录接口的JWT验证
        String requestURI = request.getRequestURI();
        logger.info("检查是否跳过请求: URI = {}", requestURI);
        
        // 对于登录和注册接口，跳过JWT验证
        if (requestURI.equals("/api/user/login") || requestURI.equals("/api/user/register")) {
            logger.info("跳过JWT验证: {}", requestURI);
            filterChain.doFilter(request, response);
            return;
        }
        
        // 处理WebSocket握手请求（强制认证）
        String upgradeHeader = request.getHeader("Upgrade");
        if ("websocket".equalsIgnoreCase(upgradeHeader) || requestURI.contains("/chat")) {
            logger.info("检测到WebSocket握手请求: {}", requestURI);
            
            // WebSocket必须带token，检查是否有token（URL参数或Header）
            String token = request.getParameter("token");
            if (token == null || token.isEmpty()) {
                // 如果没有URL参数token，检查Authorization头
                final String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }
            
            // 强制要求token
            if (token == null || token.isEmpty()) {
                logger.warn("WebSocket连接缺少token，拒绝连接: {}", requestURI);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\": 401, \"message\": \"WebSocket连接需要有效的token\"}");
                return;
            }
            
            // 验证token
            try {
                String username = jwtService.getUsernameFromToken(token);
                if (username == null) {
                    logger.warn("WebSocket token无效，无法提取用户名");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\": 401, \"message\": \"无效的token\"}");
                    return;
                }
                
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (!jwtService.validateToken(token, userDetails)) {
                    logger.warn("WebSocket token验证失败: {}", username);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"code\": 401, \"message\": \"token验证失败\"}");
                    return;
                }
                
                // token验证成功，设置认证信息
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
                logger.info("WebSocket连接已认证: {}", username);
                
            } catch (Exception e) {
                logger.error("WebSocket token验证异常: {}", e.getMessage(), e);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"code\": 401, \"message\": \"token验证失败: " + e.getMessage() + "\"}");
                return;
            }
            
            // token验证通过，允许连接
            filterChain.doFilter(request, response);
            return;
        }

        // 从请求头获取Authorization字段
        final String authHeader = request.getHeader("Authorization");
        logger.info("请求头Authorization: {}", authHeader);
        
        final String jwtToken;
        final String username;

        // 检查Authorization头格式是否正确
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.info("Authorization头缺失或格式错误，请求URI: {}", requestURI);
            
            // 对于非/api开头的请求，可以直接通过
            if (!requestURI.startsWith("/api")) {
                logger.info("非/api请求，允许通过: {}", requestURI);
                filterChain.doFilter(request, response);
                return;
            }
            // 对于/api开头的请求，需要认证
            logger.info("/api请求但无有效授权，返回401: {}", requestURI);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\": 401, \"message\": \"未授权访问，请先登录\"}");
            return;
        }

        try {
            // 提取JWT令牌
            jwtToken = authHeader.substring(7);
            logger.info("提取到JWT令牌: {}", jwtToken);
            
            // 从令牌中获取用户名
            username = jwtService.getUsernameFromToken(jwtToken);
            logger.info("从令牌中提取的用户名: {}", username);

            // 如果用户名不为空且当前上下文没有认证信息
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    // 加载用户信息
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    logger.info("成功加载用户信息: {}", username);
                    
                    // 验证令牌是否有效
                    if (jwtService.validateToken(jwtToken, userDetails)) {
                        // 创建认证令牌
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        
                        // 设置认证详情
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        
                        // 设置认证信息到安全上下文
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        logger.info("用户认证成功: {}", username);
                    } else {
                        // 令牌无效
                        logger.info("令牌无效: {}", username);
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json");
                        response.getWriter().write("{\"code\": 401, \"message\": \"无效的令牌\"}");
                        return;
                    }
                } catch (UsernameNotFoundException e) {
                    // 用户不存在
                    logger.info("用户不存在: {}", username);
                    // 继续过滤器链，让控制器处理这个情况
                }
            }
        } catch (Exception e) {
            // 令牌解析失败或其他错误
            logger.info("令牌验证异常: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\": 401, \"message\": \"令牌验证失败\"}");
            return;
        }

        // 继续过滤器链
        logger.info("JWT过滤器处理完成，继续过滤器链: {}", requestURI);
        filterChain.doFilter(request, response);
    }
}