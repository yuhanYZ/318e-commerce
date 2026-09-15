package com.csc318.ecommerce.product.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {
    private final SecretKey key;
    public JwtService(@Value("${app.jwt.secret}") String secret){this.key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));}
    public Claims parse(String token){return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();}
    public String issue(String userId,String email,String role){
        Instant now=Instant.now();
        return Jwts.builder().setSubject(userId).claim("email",email).claim("role",role).setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(3600))).signWith(key,SignatureAlgorithm.HS256).compact();
    }
}
