package com.auction.api.security;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Bỏ qua lọc với GET /api/auctions hoặc API auth, swagger
        if (path.startsWith("/swagger") || path.startsWith("/v3/api-docs") || path.startsWith("/api/auth") || (path.startsWith("/api/auctions") && method.equals("GET"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Thieu hoac sai JWT Token");
            return;
        }

        String token = authHeader.substring(7);
        try {
            // Giả lập giải mã token. Trong thực tế sẽ dùng thư viện jjwt (JwtUtils) để parse ra userId
            Long userId = 1L; // Giả sử user ID = 1 sau khi parse token thành công

            // Truyền userId xuống Controller thông qua Request Attribute
            request.setAttribute("userId", userId);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}