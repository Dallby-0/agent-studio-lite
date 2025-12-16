package com.agentworkflow.config;

import com.agentworkflow.config.properties.CorsProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
// 以下导入在未来可能用于自定义认证管理器配置
// import org.springframework.security.authentication.AuthenticationManager;
// import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
// import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Autowired
    private CorsProperties corsProperties;

    // 用户详情服务 - 目前在AuthenticationConfig中配置，未来如需自定义认证提供者可能会用到
    // @Autowired
    // private CustomUserDetailsService userDetailsService;
// 密码编码器 - 指定使用BCrypt算法，工作因子为10
    @Bean
    public PasswordEncoder passwordEncoder() {
        // 使用工作因子10，这与目标哈希值的格式匹配
        return new BCryptPasswordEncoder(10);
    }

    // 认证管理器和认证提供者已在AuthenticationConfig中配置

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // 开发环境下禁用CSRF保护
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // 无状态会话
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // 配置CORS
            .authorizeHttpRequests(authorize -> authorize
                // 允许公开访问健康检查和数据库连接相关接口
                .requestMatchers("/health/**").permitAll()
                .requestMatchers("/database/**").permitAll()
                // 允许Swagger文档访问
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // 允许登录和注册接口无需认证
                .requestMatchers("/api/user/login", "/api/user/register").permitAll()
                // 允许WebSocket连接（工作流对话界面）
                .requestMatchers("/api/workflow-instances/*/chat").permitAll()
                // 获取所有用户信息需要管理员权限
                .requestMatchers("/api/user/all").hasRole("ADMIN")
                // 其他所有/api请求都需要认证
                .requestMatchers("/api/**").authenticated()
                // 其他所有请求都允许访问
                .anyRequest().permitAll())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    
    /**
     * 配置CORS源
     * @return CORS配置源
     */
    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        
        // 从配置属性中获取允许的源
        if (corsProperties.getAllowedOrigins() != null && !corsProperties.getAllowedOrigins().equals("*")) {
            configuration.setAllowedOrigins(java.util.Arrays.asList(corsProperties.getAllowedOrigins().split(",")));
        } else {
            // 默认允许所有源（开发环境），或者配置为*时，使用AllowedOriginPatterns以支持allowCredentials
            configuration.setAllowedOriginPatterns(java.util.Collections.singletonList("*"));
        }
        
        // 从配置属性中获取允许的方法
        if (corsProperties.getAllowedMethods() != null) {
            configuration.setAllowedMethods(java.util.Arrays.asList(corsProperties.getAllowedMethods().split(",")));
        } else {
            // 默认允许的方法
            configuration.setAllowedMethods(java.util.Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        }
        
        // 从配置属性中获取允许的头
        if (corsProperties.getAllowedHeaders() != null) {
            configuration.setAllowedHeaders(java.util.Arrays.asList(corsProperties.getAllowedHeaders().split(",")));
        } else {
            // 默认允许的头
            configuration.setAllowedHeaders(java.util.Arrays.asList("*"));
        }
        
        // 允许凭证
        configuration.setAllowCredentials(true);
        
        // 设置预检请求的缓存时间
        configuration.setMaxAge(3600L);
        
        // 注册CORS配置
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}