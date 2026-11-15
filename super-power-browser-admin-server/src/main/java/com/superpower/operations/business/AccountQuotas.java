package com.superpower.operations.business;

import com.superpower.operations.config.ConfigOperator;
import com.superpower.operations.config.ConfigOperatorAuth;
import com.superpower.operations.shared.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.superpower.operations.shared.BusinessException.require;
import static com.superpower.operations.shared.Data.*;

/**
 * Platform view and mutation endpoint for per-member shop creation limits.
 * The browser service remains the source of truth for enforcement; this
 * service only edits the shared quota record and returns a consistent usage
 * snapshot for operators.
 */
@Service
public class AccountQuotas {
    private static final Logger log = LoggerFactory.getLogger(AccountQuotas.class);
    private final Db db;
    private final ConfigOperatorAuth operators;

    public AccountQuotas(Db db, ConfigOperatorAuth operators) {
        this.db = db;
        this.operators = operators;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> list(ConfigOperator actor, Map<String, String> query) {
        operators.requireActive(actor);
        require(Set.of("query", "departmentId", "tenantId", "page", "size").containsAll(query.keySet()), 400, "不支持的额度筛选项");
        Page page = page(query);
        String search = value(query, "query", "");
        String departmentId = value(query, "departmentId", "");
        String tenantId = value(query, "tenantId", "");
        require(search.length() <= 200 && departmentId.length() <= 36 && tenantId.length() <= 36, 400, "额度筛选条件不合法");
        StringBuilder where = new StringBuilder(" WHERE u.deleted=FALSE");
        List<Object> args = new ArrayList<>();
        if (!search.isEmpty()) {
            where.append(" AND (u.username LIKE ? OR u.display_name LIKE ? OR t.name LIKE ?)");
            args.add("%" + search + "%"); args.add("%" + search + "%"); args.add("%" + search + "%");
        }
        if (!departmentId.isEmpty()) { where.append(" AND u.department_id=?"); args.add(departmentId); }
        if (!tenantId.isEmpty()) { where.append(" AND u.tenant_id=?"); args.add(tenantId); }
        try {
            long total = number(one(db.select("SELECT COUNT(*) AS total FROM app_user u JOIN tenant t ON t.id=u.tenant_id" + where, args.toArray())), "total");
            args.add(page.size); args.add(page.offset());
            String columns = columns("u", "q", "t", "d");
            var records = rows(db.select("SELECT " + columns + " FROM app_user u JOIN tenant t ON t.id=u.tenant_id LEFT JOIN department d ON d.id=u.department_id LEFT JOIN shop_creation_quota q ON q.user_id=u.id" + where + " ORDER BY t.name,u.username,u.id LIMIT ? OFFSET ?", args.toArray()));
            return result(records, total, page);
        } catch (DataAccessException missingSchema) {
            log.warn("Account quota query is unavailable; complete business migration V31 before using quota management ({})", missingSchema.getMostSpecificCause().getClass().getSimpleName());
            return unavailable("业务数据库尚未完成 V31 额度迁移，请先重启主服务完成数据库升级", page);
        }
    }

    @Transactional
    public Map<String, Object> update(ConfigOperator actor, String userId, Map<String, Object> input) {
        operators.requireActive(actor);
        require(userId != null && userId.matches("[a-zA-Z0-9_-]{1,80}"), 400, "账号编号不合法");
        allowed(input, Set.of("limitCount", "enabled", "note", "revision"));
        require(input.containsKey("limitCount"), 400, "请设置店铺创建额度");
        long limit = integer(input, "limitCount", -1, 1_000_000);
        boolean enabled = bool(input, "enabled", true);
        String note = optionalString(input, "note", 500);
        try {
            Map<String, Object> user = one(db.select("SELECT u.id,u.tenant_id,u.username,u.display_name,u.department_id,d.name AS department_name,t.name AS account_name FROM app_user u JOIN tenant t ON t.id=u.tenant_id LEFT JOIN department d ON d.id=u.department_id WHERE u.id=? AND u.deleted=FALSE FOR UPDATE", userId));
            var previousRows = rows(db.select("SELECT user_id,tenant_id,limit_count,enabled,revision,note FROM shop_creation_quota WHERE user_id=? FOR UPDATE", userId));
            Map<String, Object> previous = previousRows.isEmpty() ? Map.of("revision", 0L, "limitCount", -1L, "enabled", true, "note", "") : previousRows.get(0);
            if (input.containsKey("revision")) {
                long expected = integer(input, "revision", 0, Long.MAX_VALUE);
                require(expected == number(previous, "revision"), 409, "额度配置已被其他管理员修改，请刷新后重试");
            }
            long revision = number(previous, "revision") + 1;
            if (previousRows.isEmpty()) {
                db.execute("INSERT INTO shop_creation_quota(user_id,tenant_id,limit_count,enabled,revision,note,updated_by) VALUES(?,?,?,?,?,?,?)", userId, text(user, "tenantId"), limit, enabled, revision, note, actor.id());
            } else {
                db.execute("UPDATE shop_creation_quota SET limit_count=?,enabled=?,revision=?,note=?,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", limit, enabled, revision, note, actor.id(), userId);
            }
            audit(actor, text(user, "tenantId"), userId, previousRows.isEmpty() ? null : number(previous, "limitCount"), limit,
                previousRows.isEmpty() ? null : flag(previous, "enabled"), enabled, previousRows.isEmpty() ? null : number(previous, "revision"), revision, note);
            return detail(userId);
        } catch (DataAccessException missingSchema) {
            // Keep the response contract explicit when the browser migration is not deployed yet.
            log.warn("Account quota update is unavailable; complete business migration V31 before using quota management ({})", missingSchema.getMostSpecificCause().getClass().getSimpleName());
            throw new BusinessException(503, "业务数据库尚未完成 V31 额度迁移，请先重启主服务完成数据库升级");
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> audit(ConfigOperator actor, String userId, Map<String, String> query) {
        operators.requireActive(actor);
        require(userId != null && userId.matches("[a-zA-Z0-9_-]{1,80}"), 400, "账号编号不合法");
        Page page = page(query);
        require(query.keySet().stream().allMatch(key -> Set.of("page", "size").contains(key)), 400, "不支持的审计筛选项");
        require(!db.select("SELECT id FROM app_user WHERE id=? AND deleted=FALSE", userId).isEmpty(), 404, "账号不存在");
        var args = new Object[]{userId, page.size, page.offset()};
        var items = rows(db.select("SELECT id,user_id,operator_id,operator_name,before_limit,after_limit,before_enabled,after_enabled,before_revision,after_revision,reason,created_at FROM shop_quota_audit WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?", args));
        long total = number(one(db.select("SELECT COUNT(*) AS total FROM shop_quota_audit WHERE user_id=?", userId)), "total");
        return result(items, total, page);
    }

    private Map<String, Object> detail(String userId) {
        var rows = rows(db.select("SELECT " + columns("u", "q", "t", "d") + " FROM app_user u JOIN tenant t ON t.id=u.tenant_id LEFT JOIN department d ON d.id=u.department_id LEFT JOIN shop_creation_quota q ON q.user_id=u.id WHERE u.id=?", userId));
        require(!rows.isEmpty(), 404, "账号不存在");
        return rows.get(0);
    }

    /** Supports both the original UUID audit key and deployments using an identity key. */
    private void audit(ConfigOperator actor, String tenantId, String userId, Object beforeLimit, long afterLimit,
                       Object beforeEnabled, boolean afterEnabled, Object beforeRevision, long afterRevision, String reason) {
        var types = db.select("SELECT data_type FROM information_schema.columns WHERE LOWER(table_name)=? AND LOWER(column_name)=?", "shop_quota_audit", "id");
        boolean numericId = !types.isEmpty() && text(row(types.get(0)), "dataType").toLowerCase(Locale.ROOT).contains("int");
        if (numericId) {
            db.execute("INSERT INTO shop_quota_audit(tenant_id,user_id,operator_id,operator_name,before_limit,after_limit,before_enabled,after_enabled,before_revision,after_revision,reason) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                tenantId, userId, actor.id(), actor.username(), beforeLimit, afterLimit, beforeEnabled, afterEnabled, beforeRevision, afterRevision, reason);
        } else {
            db.execute("INSERT INTO shop_quota_audit(id,tenant_id,user_id,operator_id,operator_name,before_limit,after_limit,before_enabled,after_enabled,before_revision,after_revision,reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                id(), tenantId, userId, actor.id(), actor.username(), beforeLimit, afterLimit, beforeEnabled, afterEnabled, beforeRevision, afterRevision, reason);
        }
    }

    private static String columns(String user, String quota, String tenant, String department) {
        String used = "(SELECT COUNT(*) FROM environment e WHERE e.tenant_id=" + user + ".tenant_id AND e.owner_id=" + user + ".id)";
        return user + ".id AS user_id," + user + ".tenant_id," + tenant + ".name AS account_name," + user + ".username," + user + ".display_name," + user + ".department_id," + department + ".name AS department_name," +
            "COALESCE(" + quota + ".limit_count,-1) AS limit_count," + used + " AS used_count," +
            "CASE WHEN COALESCE(" + quota + ".enabled,TRUE)=FALSE OR COALESCE(" + quota + ".limit_count,-1)<0 THEN -1 ELSE GREATEST(COALESCE(" + quota + ".limit_count,-1)-" + used + ",0) END AS remaining_count," +
            "COALESCE(" + quota + ".enabled,TRUE) AS enabled,COALESCE(" + quota + ".revision,0) AS revision,COALESCE(" + quota + ".note,'') AS note," + quota + ".updated_at";
    }

    private record Page(int number, int size) { int offset() { return (number - 1) * size; } }
    private static Page page(Map<String, String> query) { return new Page(integer(query, "page", 1, 1, Integer.MAX_VALUE), integer(query, "size", 20, 1, 100)); }
    private static String value(Map<String, String> query, String key, String fallback) { String value = query.get(key); return value == null || value.isBlank() ? fallback : value.trim(); }
    private static int integer(Map<String, String> query, String key, int fallback, int min, int max) { String raw = value(query, key, Integer.toString(fallback)); require(raw.matches("-?[0-9]{1,10}"), 400, "分页参数不合法"); long parsed = Long.parseLong(raw); require(parsed >= min && parsed <= max, 400, "分页参数超出范围"); return (int) parsed; }
    private static long integer(Map<String, Object> input, String key, long min, long max) { Object raw = input.get(key); require(raw instanceof Number && text(input, key).matches("-?[0-9]{1,12}"), 400, "字段须为整数：" + key); long value = number(input, key); require(value >= min && value <= max, 400, "数值超出范围：" + key); return value; }
    private static String optionalString(Map<String, Object> input, String key, int max) { Object raw = input.getOrDefault(key, ""); require(raw instanceof String, 400, "字段须为文本：" + key); String value = (String) raw; require(value.length() <= max && !value.matches("(?s).*[\\x00-\\x1f\\x7f].*"), 400, "字段长度或字符不合法：" + key); return value; }
    private static boolean bool(Map<String, Object> input, String key, boolean fallback) { if (!input.containsKey(key)) return fallback; require(input.get(key) instanceof Boolean, 400, "字段须为布尔值：" + key); return (Boolean) input.get(key); }
    private static void allowed(Map<String, Object> input, Set<String> keys) { require(input != null && keys.containsAll(input.keySet()), 400, "额度配置包含不支持的字段"); }
    private static Map<String, Object> result(List<Map<String, Object>> records, long total, Page page) { var value = new LinkedHashMap<String, Object>(); value.put("items", records); value.put("total", total); value.put("page", page.number); value.put("size", page.size); value.put("integrationRequired", false); return value; }
    private static Map<String, Object> unavailable(String message, Page page) { var value = new LinkedHashMap<String, Object>(); value.put("items", List.of()); value.put("total", 0); value.put("page", page.number); value.put("size", page.size); value.put("integrationRequired", true); value.put("message", message); return value; }
}
