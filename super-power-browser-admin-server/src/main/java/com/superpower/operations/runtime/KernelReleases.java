package com.superpower.operations.runtime;

import com.superpower.operations.config.ConfigOperator;
import com.superpower.operations.config.ConfigOperatorAuth;
import com.superpower.operations.shared.Db;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.superpower.operations.shared.BusinessException.require;
import static com.superpower.operations.shared.Data.*;

@Service
public class KernelReleases {
    private final Db db;
    private final ConfigOperatorAuth operators;
    private final KernelStorage storage;

    public KernelReleases(Db db, ConfigOperatorAuth operators, KernelStorage storage) {
        this.db = db; this.operators = operators; this.storage = storage;
    }

    public List<Map<String, Object>> list(ConfigOperator operator) {
        operators.requireActive(operator);
        return rows(db.select("SELECT * FROM kernel_release ORDER BY created_at DESC,id")).stream().map(this::view).toList();
    }

    @Transactional
    public Map<String, Object> create(ConfigOperator operator, Map<String, Object> request) {
        operators.requireActive(operator);
        require(request != null && request.get("descriptor") instanceof Map<?, ?>, 400, "请导入有效的内核清单");
        @SuppressWarnings("unchecked") Map<String, Object> input = (Map<String, Object>) request.get("descriptor");
        var descriptor = KernelDescriptor.validate(input);
        String version = text(descriptor, "version"), platform = text(descriptor, "platform"), arch = text(descriptor, "arch");
        require(db.select("SELECT id FROM kernel_release WHERE version=? AND platform=? AND arch=?", version, platform, arch).isEmpty(),
            409, "相同平台和架构的内核版本已经存在");
        String id = id(), key = storage.objectKey(descriptor);
        db.execute("INSERT INTO kernel_release(id,version,platform,arch,full_version,sha256,archive_bytes,descriptor,storage_type,storage_key,state,created_by) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            id, version, platform, arch, text(descriptor, "fullVersion"), text(descriptor, "sha256"), number(descriptor, "archiveBytes"),
            json(descriptor), "S3", key, "DRAFT", operator.id());
        var result = view(one(db.select("SELECT * FROM kernel_release WHERE id=?", id)));
        audit(operator, "kernel.create", result, null);
        return result;
    }

    public Map<String, Object> upload(ConfigOperator operator, String id) {
        operators.requireActive(operator);
        var entry = row(id); require(text(entry, "state").equals("DRAFT"), 409, "已发布或已停用的版本不能覆盖上传");
        return storage.upload(entry);
    }

    public Map<String, Object> download(ConfigOperator operator, String id) {
        operators.requireActive(operator);
        var entry = row(id); require(text(entry, "state").equals("PUBLISHED"), 409, "只有已发布版本可以下载");
        return storage.download(entry);
    }

    @Transactional
    public Map<String, Object> publish(ConfigOperator operator, String id, Map<String, Object> request) {
        operators.requireActive(operator);
        require(request != null && request.containsKey("revision"), 400, "缺少版本校验信息，请刷新后重试");
        var before = row(id); require(text(before, "state").equals("DRAFT"), 409, "只有草稿版本可以发布");
        String objectVersion = storage.verify(before);
        lock();
        var current = row(id);
        require(text(current, "state").equals("DRAFT") && number(request, "revision") == number(current, "revision"), 409, "版本状态已改变，请刷新后重试");
        boolean recommended = flag(request, "recommended");
        if (recommended) clearRecommended(current);
        db.execute("UPDATE kernel_release SET state='PUBLISHED',recommended=?,object_version=?,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            recommended, objectVersion, id);
        var result = view(row(id)); audit(operator, "kernel.publish", result, view(current)); return result;
    }

    @Transactional
    public Map<String, Object> recommend(ConfigOperator operator, String id) {
        operators.requireActive(operator); lock();
        var current = row(id); require(text(current, "state").equals("PUBLISHED"), 409, "只有已发布版本可以设为推荐");
        clearRecommended(current);
        db.execute("UPDATE kernel_release SET recommended=TRUE,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        var result = view(row(id)); audit(operator, "kernel.recommend", result, view(current)); return result;
    }

    @Transactional
    public Map<String, Object> disable(ConfigOperator operator, String id) {
        operators.requireActive(operator); lock();
        var current = row(id); require(!text(current, "state").equals("DISABLED"), 409, "该内核已经停用");
        db.execute("UPDATE kernel_release SET state='DISABLED',recommended=FALSE,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        var result = view(row(id)); audit(operator, "kernel.disable", result, view(current)); return result;
    }

    private void clearRecommended(Map<String, Object> entry) {
        db.execute("UPDATE kernel_release SET recommended=FALSE,revision=revision+1,updated_at=CURRENT_TIMESTAMP WHERE platform=? AND arch=? AND recommended=TRUE",
            text(entry, "platform"), text(entry, "arch"));
    }

    private void lock() { db.select("SELECT id FROM kernel_catalog_lock WHERE id=1 FOR UPDATE"); }
    private Map<String, Object> row(String id) { return one(db.select("SELECT * FROM kernel_release WHERE id=?", id)); }

    private Map<String, Object> view(Map<String, Object> row) {
        var result = new LinkedHashMap<>(row);
        result.put("descriptor", object(text(row, "descriptor")));
        return result;
    }

    private void audit(ConfigOperator operator, String action, Map<String, Object> after, Map<String, Object> before) {
        db.execute("INSERT INTO config_audit(id,config_key,operator_id,operator_name,revision,before_json,after_json) VALUES(?,?,?,?,?,?,?)",
            id(), "kernel-releases", operator.id(), operator.username(), number(after, "revision"), json(before), json(Map.of("action", action, "release", after)));
    }
}
