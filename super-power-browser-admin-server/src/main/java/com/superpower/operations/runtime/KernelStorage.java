package com.superpower.operations.runtime;

import com.superpower.operations.shared.BusinessException;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;
import static com.superpower.operations.shared.BusinessException.require;
import static com.superpower.operations.shared.Data.*;

/** Object storage access for kernel upload verification and short-lived downloads. */
@Component
public class KernelStorage {
    private final String bucket, endpoint, region, prefix;
    private final boolean pathStyle, allowHttp;
    private final int urlMinutes;
    private S3Client client;
    private S3Presigner signer;
    private DefaultCredentialsProvider credentials;

    public KernelStorage(
        @Value("${KERNEL_S3_BUCKET:}") String bucket,
        @Value("${KERNEL_S3_ENDPOINT:}") String endpoint,
        @Value("${KERNEL_S3_REGION:us-east-1}") String region,
        @Value("${KERNEL_S3_PREFIX:kernels}") String prefix,
        @Value("${KERNEL_S3_PATH_STYLE:false}") boolean pathStyle,
        @Value("${KERNEL_S3_ALLOW_HTTP:false}") boolean allowHttp,
        @Value("${KERNEL_URL_MINUTES:15}") int urlMinutes) {
        this.bucket = bucket; this.endpoint = endpoint; this.region = region; this.prefix = prefix;
        this.pathStyle = pathStyle; this.allowHttp = allowHttp; this.urlMinutes = urlMinutes;
    }

    private synchronized void connect() {
        if (client != null) return;
        require(!bucket.isBlank(), 503, "尚未配置内核对象存储 bucket");
        require(urlMinutes >= 1 && urlMinutes <= 60, 503, "内核下载链接有效期必须为 1 至 60 分钟");
        URI endpointUri = null;
        if (!endpoint.isBlank()) {
            endpointUri = URI.create(endpoint);
            require(endpointUri.getHost() != null && endpointUri.getUserInfo() == null
                    && endpointUri.getQuery() == null && endpointUri.getFragment() == null
                    && (endpointUri.getPath().isEmpty() || endpointUri.getPath().equals("/")),
                503, "对象存储端点不合法");
            require("https".equals(endpointUri.getScheme())
                    || (allowHttp && "http".equals(endpointUri.getScheme())
                    && Set.of("127.0.0.1", "localhost", "[::1]").contains(endpointUri.getHost())),
                503, "对象存储必须使用 HTTPS");
        }
        credentials = DefaultCredentialsProvider.builder().build();
        var service = S3Configuration.builder().pathStyleAccessEnabled(pathStyle).checksumValidationEnabled(false).build();
        var builder = S3Client.builder().region(Region.of(region)).credentialsProvider(credentials).serviceConfiguration(service)
            .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(10)).socketTimeout(Duration.ofSeconds(30)))
            .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofMinutes(3)).apiCallAttemptTimeout(Duration.ofMinutes(2)));
        var signerBuilder = S3Presigner.builder().region(Region.of(region)).credentialsProvider(credentials).serviceConfiguration(service);
        if (endpointUri != null) { builder.endpointOverride(endpointUri); signerBuilder.endpointOverride(endpointUri); }
        client = builder.build(); signer = signerBuilder.build();
    }

    public String objectKey(Map<String, Object> descriptor) {
        String cleanPrefix = prefix.replaceAll("^/+|/+$", "");
        KernelDescriptor.relative(cleanPrefix);
        return cleanPrefix + "/" + text(descriptor, "platform") + "/" + text(descriptor, "arch") + "/"
            + text(descriptor, "version") + "/" + text(descriptor, "sha256") + ".zip";
    }

    public Map<String, Object> upload(Map<String, Object> entry) {
        require(text(entry, "storageType").equals("S3"), 409, "仅对象存储版本支持上传");
        connect();
        try {
            String checksum = Base64.getEncoder().encodeToString(HexFormat.of().parseHex(text(entry, "sha256")));
            var request = PutObjectRequest.builder().bucket(bucket).key(text(entry, "storageKey"))
                .contentType("application/zip").contentLength(number(entry, "archiveBytes"))
                .checksumSHA256(checksum).ifNoneMatch("*").metadata(Map.of("sha256", text(entry, "sha256"))).build();
            var signed = signer.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(urlMinutes)).putObjectRequest(request).build());
            var headers = new LinkedHashMap<String, String>();
            signed.signedHeaders().forEach((key, values) -> { if (!key.equalsIgnoreCase("host")) headers.put(key, String.join(",", values)); });
            return Map.of("url", signed.url().toString(), "method", "PUT", "headers", headers, "expiresAt", signed.expiration().toString());
        } catch (RuntimeException e) {
            throw new BusinessException(503, "无法签发内核上传地址，请检查对象存储配置");
        }
    }

    public String verify(Map<String, Object> entry) {
        connect();
        try {
            var headRequest = HeadObjectRequest.builder().bucket(bucket).key(text(entry, "storageKey"));
            if (!text(entry, "objectVersion").isBlank()) headRequest.versionId(text(entry, "objectVersion"));
            var head = client.headObject(headRequest.build());
            require(head.contentLength() == number(entry, "archiveBytes"), 409, "对象存储内核包大小不匹配");
            String version = head.versionId(); if (version == null || version.equals("null")) version = "";
            var get = GetObjectRequest.builder().bucket(bucket).key(text(entry, "storageKey"));
            if (!version.isBlank()) get.versionId(version);
            try (InputStream stream = client.getObject(get.build())) { verifyBytes(stream, entry); }
            return version;
        } catch (BusinessException e) { throw e; }
        catch (Exception e) { throw new BusinessException(503, "无法校验内核包，请检查对象存储权限"); }
    }

    public Map<String, Object> download(Map<String, Object> entry) {
        connect();
        try {
            var request = GetObjectRequest.builder().bucket(bucket).key(text(entry, "storageKey"));
            if (!text(entry, "objectVersion").isBlank()) request.versionId(text(entry, "objectVersion"));
            var signed = signer.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(urlMinutes)).getObjectRequest(request.build()).build());
            require(signed.isBrowserExecutable(), 503, "对象存储下载地址需要额外签名头");
            return Map.of("url", signed.url().toString(), "expiresAt", signed.expiration().toString());
        } catch (BusinessException e) { throw e; }
        catch (RuntimeException e) { throw new BusinessException(503, "无法签发内核下载地址，请检查对象存储配置"); }
    }

    private void verifyBytes(InputStream stream, Map<String, Object> entry) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256"); long size = 0, expected = number(entry, "archiveBytes");
        byte[] buffer = new byte[128 * 1024]; int count;
        while ((count = stream.read(buffer)) != -1) { size += count; require(size <= expected, 409, "内核包长度超出清单"); digest.update(buffer, 0, count); }
        require(size == expected && HexFormat.of().formatHex(digest.digest()).equals(text(entry, "sha256")),
            409, "内核包 SHA-256 与清单不一致，禁止发布");
    }

    @PreDestroy public synchronized void close() {
        if (client != null) client.close(); if (signer != null) signer.close(); if (credentials != null) credentials.close();
    }
}
