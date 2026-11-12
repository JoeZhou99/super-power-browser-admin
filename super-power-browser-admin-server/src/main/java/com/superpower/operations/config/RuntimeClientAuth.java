package com.superpower.operations.config;

import com.superpower.operations.shared.Db;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import static com.superpower.operations.shared.BusinessException.require;
import static com.superpower.operations.shared.Data.*;

/** Read-only verification of the business service's existing customer sessions. */
@Service
public class RuntimeClientAuth {
    public record Client(String id,String tenantId) {}
    private final Db db;
    public RuntimeClientAuth(Db db) { this.db=db; }
    public Client authenticate(String token) {
        require(!token.isBlank()&&token.length()<=200,401,"客户登录已失效，请重新登录");
        var users=rows(db.select("SELECT u.id,u.tenant_id FROM auth_session s JOIN app_user u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP AND u.enabled=TRUE AND u.deleted=FALSE",hash(token)));
        require(!users.isEmpty(),401,"客户登录已失效，请重新登录");
        return new Client(text(users.get(0),"id"),text(users.get(0),"tenantId"));
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
