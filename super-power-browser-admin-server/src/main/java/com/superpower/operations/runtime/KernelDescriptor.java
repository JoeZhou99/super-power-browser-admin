package com.superpower.operations.runtime;

import java.util.*;
import static com.superpower.operations.shared.BusinessException.require;
import static com.superpower.operations.shared.Data.*;

/** Validates the immutable descriptor imported from the kernel build pipeline. */
public final class KernelDescriptor {
    private KernelDescriptor() {}

    public static boolean version(String value) {
        return value != null && value.matches("[0-9]{2,3}(?:\\.[0-9]+){0,3}");
    }

    public static void platform(String platform, String arch) {
        require(platform.equals("win32") && arch.equals("x64")
                || platform.equals("darwin") && Set.of("x64", "arm64").contains(arch),
            400, "仅支持 Windows x64、Mac Intel 和 Apple Silicon");
    }

    public static String relative(String value) {
        require(value != null && !value.isBlank() && value.length() <= 500
                && !value.startsWith("/") && !value.contains("\\")
                && !value.matches(".*[\\x00-\\x1f:].*"), 400, "内核文件路径不合法");
        for (String part : value.split("/", -1)) {
            require(!part.isEmpty() && !part.endsWith(".") && !part.endsWith(" ")
                    && !part.matches("(?i)(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\\..*)?"),
                400, "内核文件路径不合法");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> validate(Map<String, Object> input) {
        require(input != null && json(input).length() <= 4 * 1024 * 1024, 400, "内核清单过大");
        var d = new LinkedHashMap<String, Object>();
        for (String key : List.of("version", "fullVersion", "platform", "arch", "protocol", "provider",
                "executable", "sha256", "archiveBytes", "sourceCommit", "sourceUrl", "files", "symlinks",
                "executableFiles", "requiredDirectories", "capabilities")) {
            if (input.containsKey(key)) d.put(key, input.get(key));
        }
        require(version(text(d, "version")) && version(text(d, "fullVersion"))
                && text(d, "version").split("\\.")[0].equals(text(d, "fullVersion").split("\\.")[0]),
            400, "内核版本不合法或主版本不一致");
        platform(text(d, "platform"), text(d, "arch"));
        require(text(d, "protocol").equals("fingerprint-chromium/1"), 400, "内核协议尚未适配");
        require(text(d, "sha256").matches("[a-fA-F0-9]{64}"), 400, "内核 SHA-256 不合法");
        d.put("sha256", text(d, "sha256").toLowerCase(Locale.ROOT));
        long bytes = number(d, "archiveBytes");
        require(bytes > 0 && bytes <= 2L * 1024 * 1024 * 1024, 400, "内核包大小不合法");
        d.put("archiveBytes", bytes);
        String executable = relative(text(d, "executable"));
        boolean mac = text(d, "platform").equals("darwin");
        require(mac ? executable.matches(".+\\.app/Contents/MacOS/[^/]+$") : executable.endsWith(".exe"),
            400, "内核启动程序与目标系统不匹配");
        require(d.get("files") instanceof Map<?, ?>, 400, "缺少文件校验清单");
        Map<?, ?> files = (Map<?, ?>) d.get("files");
        require(!files.isEmpty() && files.size() <= 20000 && files.containsKey(executable), 400, "文件校验清单不完整");
        var seen = new HashSet<String>();
        for (var entry : files.entrySet()) {
            require(entry.getKey() instanceof String && entry.getValue() instanceof String, 400, "文件校验清单不合法");
            String name = relative(entry.getKey().toString());
            require(seen.add(name.toLowerCase(Locale.ROOT)) && entry.getValue().toString().matches("[a-fA-F0-9]{64}"),
                400, "文件校验值或重复路径不合法");
        }
        if (d.containsKey("symlinks")) {
            require(mac && d.get("symlinks") instanceof Map<?, ?>, 400, "内核链接清单不合法");
            for (var entry : ((Map<?, ?>) d.get("symlinks")).entrySet()) {
                require(entry.getKey() instanceof String && entry.getValue() instanceof String, 400, "内核链接清单不合法");
                String name = relative(entry.getKey().toString());
                require(seen.add(name.toLowerCase(Locale.ROOT)), 400, "内核文件路径重复");
                linkTarget(name, entry.getValue().toString());
            }
        }
        if (d.containsKey("executableFiles")) {
            for (String name : strings(d, "executableFiles")) require(files.containsKey(name), 400, "执行权限清单包含未知文件");
            if (mac) require(strings(d, "executableFiles").contains(executable), 400, "Mac 内核缺少可执行文件权限");
        }
        if (d.containsKey("requiredDirectories")) for (String dir : strings(d, "requiredDirectories")) relative(dir);
        if (d.containsKey("capabilities")) require(d.get("capabilities") instanceof Map<?, ?>, 400, "内核能力清单不合法");
        if (d.containsKey("symlinks")) {
            Map<?, ?> links = (Map<?, ?>) d.get("symlinks");
            for (Object name : links.keySet()) resolveLink(name.toString(), links, files, new HashSet<>());
        }
        d.put("requiresLegacyAuthorization", false);
        return d;
    }

    private static String linkTarget(String name, String target) {
        require(!target.isBlank() && !target.startsWith("/") && !target.contains("\\")
                && !target.matches(".*[\\x00-\\x1f:].*"), 400, "内核链接目标不合法");
        var parts = new ArrayDeque<String>();
        String parent = name.contains("/") ? name.substring(0, name.lastIndexOf('/') + 1) : "";
        for (String part : (parent + target).split("/")) {
            if (part.equals(".") || part.isEmpty()) continue;
            if (part.equals("..")) { require(!parts.isEmpty(), 400, "内核链接越界"); parts.removeLast(); }
            else parts.addLast(part);
        }
        return relative(String.join("/", parts));
    }

    private static void resolveLink(String name, Map<?, ?> links, Map<?, ?> files, Set<String> visited) {
        String[] parts = name.split("/"); String prefix = "";
        for (int i = 0; i < parts.length; i++) {
            prefix += (i == 0 ? "" : "/") + parts[i];
            if (!links.containsKey(prefix)) continue;
            require(visited.size() < 40 && visited.add(prefix), 400, "内核链接存在循环");
            String suffix = i + 1 < parts.length ? "/" + String.join("/", Arrays.copyOfRange(parts, i + 1, parts.length)) : "";
            resolveLink(linkTarget(prefix, links.get(prefix).toString()) + suffix, links, files, visited);
            return;
        }
        require(files.containsKey(name) || files.keySet().stream().anyMatch(key -> key.toString().startsWith(name + "/")), 400, "内核链接目标不存在");
    }
}
