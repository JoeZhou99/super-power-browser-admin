package com.superpower.operations.relay;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.superpower.operations.shared.BusinessException;
import static com.superpower.operations.shared.BusinessException.require;

/** Shared AES-GCM storage contract; the operations service does not depend on the business binary. */
@Component
public class RelaySecrets {
    private final String configuredKey;
    public RelaySecrets(@Value("${APP_DATA_KEY:}") String key) { configuredKey=key; }
    public String encrypt(String password) {
        require(!configuredKey.isBlank(),503,"请配置与业务服务一致的 APP_DATA_KEY 后再保存中继凭据");
        try {
            byte[] key=Base64.getDecoder().decode(configuredKey);
            require(key.length==32,503,"APP_DATA_KEY 必须是 32 字节密钥的 Base64 编码");
            byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
            return Base64.getEncoder().encodeToString(nonce)+":"+Base64.getEncoder().encodeToString(cipher.doFinal(password.getBytes(StandardCharsets.UTF_8)));
        }catch(BusinessException e){throw e;}
        catch(IllegalArgumentException e){throw new BusinessException(503,"APP_DATA_KEY 格式不正确");}
        catch(Exception e){throw new IllegalStateException("中继凭据加密失败",e);}
    }
    public String decrypt(String stored) {
        if(stored==null||stored.isEmpty()) return "";
        try {
            byte[] key=Base64.getDecoder().decode(configuredKey);require(key.length==32,503,"APP_DATA_KEY 必须是 32 字节密钥的 Base64 编码");
            String[] parts=stored.split(":",2);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])),StandardCharsets.UTF_8);
        } catch(BusinessException e){throw e;} catch(Exception e){throw new BusinessException(503,"免费代理凭据解密失败，请检查 APP_DATA_KEY");}
    }
}
