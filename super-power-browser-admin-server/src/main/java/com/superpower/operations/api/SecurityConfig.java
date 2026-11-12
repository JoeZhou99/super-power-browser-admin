package com.superpower.operations.api;

import com.superpower.operations.config.ConfigOperatorAuth;
import com.superpower.operations.config.RuntimeClientAuth;
import com.superpower.operations.shared.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain chain(HttpSecurity http,ConfigOperatorAuth operators,RuntimeClientAuth clients) throws Exception {
        return http.csrf(c->c.disable()).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(c->c.requestMatchers("/api/admin/auth/login","/api/admin/health","/api/health").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/runtime/config").hasAuthority("RUNTIME_CLIENT")
                .requestMatchers(HttpMethod.GET,"/api/admin/customer-proxies","/api/admin/customer-proxies/**").hasAuthority("CONFIG_OPERATOR")
                .requestMatchers(HttpMethod.PUT,"/api/admin/customer-proxies/*/relays").hasAuthority("CONFIG_OPERATOR")
                .requestMatchers(HttpMethod.POST,"/api/admin/customer-proxies/relays/batch","/api/admin/customer-proxies/relays/allocate").hasAuthority("CONFIG_OPERATOR")
                .requestMatchers("/api/admin/customer-proxies","/api/admin/customer-proxies/**").denyAll()
                .requestMatchers("/api/admin/**").hasAuthority("CONFIG_OPERATOR").anyRequest().denyAll())
            .exceptionHandling(c->c.authenticationEntryPoint((req,res,e)->error(res,401,"请先登录"))
                .accessDeniedHandler((req,res,e)->error(res,403,"没有访问权限")))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                    String header=req.getHeader("Authorization");
                    if(header!=null&&header.startsWith("Bearer ")) {
                        try {
                            boolean runtime=req.getRequestURI().equals("/api/runtime/config");
                            Object actor=runtime?clients.authenticate(header.substring(7)):operators.authenticate(header.substring(7));
                            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,List.of(new SimpleGrantedAuthority(runtime?"RUNTIME_CLIENT":"CONFIG_OPERATOR"))));
                        } catch(BusinessException e) { error(res,e.status,e.getMessage());return; }
                    }
                    chain.doFilter(req,res);
                }
            },UsernamePasswordAuthenticationFilter.class).build();
    }
    private static void error(HttpServletResponse res,int status,String message) throws IOException {
        res.setStatus(status);res.setContentType("application/json;charset=UTF-8");res.getWriter().write(Data.json(java.util.Map.of("message",message)));
    }
}
