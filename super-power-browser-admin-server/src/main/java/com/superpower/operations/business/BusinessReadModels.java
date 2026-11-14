package com.superpower.operations.business;

import com.superpower.operations.config.ConfigOperator;
import com.superpower.operations.config.ConfigOperatorAuth;
import com.superpower.operations.shared.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.superpower.operations.shared.BusinessException.require;
import static com.superpower.operations.shared.Data.*;

/** Read-only adapters for the first business migration slice. */
@Service
public class BusinessReadModels {
    private final Db db;
    private final ConfigOperatorAuth operators;
    public BusinessReadModels(Db db, ConfigOperatorAuth operators) { this.db = db; this.operators = operators; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> members(ConfigOperator actor, Map<String, String> query) {
        operators.requireActive(actor);
        Page page = page(query); String search = value(query, "query", ""); String status = value(query, "status", "");
        require(search.length() <= 200 && status.length() <= 32, 400, "成员筛选条件不合法");
        StringBuilder where = new StringBuilder(" WHERE 1=1"); List<Object> args = new ArrayList<>();
        if (!search.isEmpty()) { where.append(" AND (u.username LIKE ? OR u.phone LIKE ? OR a.name LIKE ?)"); args.add("%" + search + "%"); args.add("%" + search + "%"); args.add("%" + search + "%"); }
        if (!status.isEmpty()) { where.append(" AND m.status=?"); args.add(status); }
        try {
            long total = count("SELECT COUNT(*) AS total FROM account_members m JOIN users u ON u.id=m.user_id JOIN accounts a ON a.id=m.account_id" + where, args);
            args.add(page.size); args.add(page.offset());
            var records = rows(db.select("SELECT m.id,m.account_id,a.name AS account_name,m.user_id,u.username,u.phone,m.member_role,m.status,m.permissions_json,m.created_at FROM account_members m JOIN users u ON u.id=m.user_id JOIN accounts a ON a.id=m.account_id" + where + " ORDER BY m.created_at DESC,m.id DESC LIMIT ? OFFSET ?", args.toArray()));
            return result(records, total, page);
        } catch (RuntimeException missingSchema) { return unavailable("用户/成员表尚未接入新后台数据库", page); }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> orders(ConfigOperator actor, Map<String, String> query) {
        operators.requireActive(actor);
        Page page = page(query); String search = value(query, "query", ""); String status = value(query, "status", ""); String paymentState = value(query, "paymentState", ""); String businessType = value(query, "businessType", "");
        require(search.length() <= 200 && status.length() <= 32 && paymentState.length() <= 32 && businessType.length() <= 64, 400, "订单筛选条件不合法");
        StringBuilder where = new StringBuilder(" WHERE 1=1"); List<Object> args = new ArrayList<>();
        if (!search.isEmpty()) { where.append(" AND (o.order_no LIKE ? OR CAST(o.account_id AS CHAR) LIKE ? OR a.name LIKE ?)"); args.add("%" + search + "%"); args.add("%" + search + "%"); args.add("%" + search + "%"); }
        if (!status.isEmpty()) { where.append(" AND o.state=?"); args.add(status); }
        if (!paymentState.isEmpty()) { where.append(" AND o.payment_state=?"); args.add(paymentState); }
        if (!businessType.isEmpty()) { where.append(" AND o.business_type=?"); args.add(businessType); }
        try {
            long total = count("SELECT COUNT(*) AS total FROM orders o JOIN accounts a ON a.id=o.account_id" + where, args);
            args.add(page.size); args.add(page.offset());
            var records = rows(db.select("SELECT o.id,o.account_id,a.name AS account_name,o.order_no,o.business_type,o.state,o.payment_state,o.fulfillment_state,o.currency,o.gross_amount,o.due_amount,o.expires_at,o.created_at,o.updated_at FROM orders o JOIN accounts a ON a.id=o.account_id" + where + " ORDER BY o.created_at DESC,o.id DESC LIMIT ? OFFSET ?", args.toArray()));
            return result(records, total, page);
        } catch (RuntimeException missingSchema) { return unavailable("订单表尚未接入新后台数据库", page); }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> skills(ConfigOperator actor, Map<String, String> query) {
        operators.requireActive(actor);
        Page page = page(query); String search = value(query, "query", ""); String status = value(query, "status", ""); String reviewStatus = value(query, "reviewStatus", ""); String source = value(query, "source", "");
        require(search.length() <= 200 && status.length() <= 32 && reviewStatus.length() <= 32 && source.length() <= 32, 400, "技能筛选条件不合法");
        StringBuilder where = new StringBuilder(" WHERE 1=1"); List<Object> args = new ArrayList<>();
        if (!search.isEmpty()) { where.append(" AND (name LIKE ? OR author LIKE ?)"); args.add("%" + search + "%"); args.add("%" + search + "%"); }
        if (!status.isEmpty()) { where.append(" AND status=?"); args.add(status); }
        if (!reviewStatus.isEmpty()) { where.append(" AND review_status=?"); args.add(reviewStatus); }
        if (!source.isEmpty()) { where.append(" AND source=?"); args.add(source); }
        try {
            long total = count("SELECT COUNT(*) AS total FROM skill_catalog" + where, args);
            args.add(page.size); args.add(page.offset());
            var records = rows(db.select("SELECT id,name,version,category_name,platform_name,source,author,scope,review_status,review_reason,file_md5,status,download_count,created_at,updated_at FROM skill_catalog" + where + " ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?", args.toArray()));
            return result(records, total, page);
        } catch (RuntimeException missingSchema) { return unavailable("技能目录表尚未接入新后台数据库", page); }
    }

    private long count(String sql, List<Object> args) { var records = db.select(sql, args.toArray()); return records.isEmpty() ? 0 : Long.parseLong(text(row(records.get(0)), "total")); }
    private static Map<String, Object> result(List<Map<String, Object>> records, long total, Page page) { var result = new LinkedHashMap<String, Object>(); result.put("items", records); result.put("total", total); result.put("page", page.number); result.put("size", page.size); result.put("integrationRequired", false); return result; }
    private static Map<String, Object> unavailable(String message, Page page) { var result = new LinkedHashMap<String, Object>(); result.put("items", List.of()); result.put("total", 0); result.put("page", page.number); result.put("size", page.size); result.put("integrationRequired", true); result.put("message", message); return result; }
    private static Page page(Map<String, String> query) { return new Page(integer(query, "page", 1, 1, Integer.MAX_VALUE), integer(query, "size", 20, 1, 100)); }
    private static String value(Map<String, String> query, String key, String fallback) { String value = query.get(key); return value == null || value.isBlank() ? fallback : value.trim(); }
    private record Page(int number, int size) { int offset() { return (number - 1) * size; } }
    private static int integer(Map<String, String> query, String key, int fallback, int min, int max) { String raw = value(query, key, Integer.toString(fallback)); require(raw.matches("[0-9]{1,10}"), 400, "分页参数不合法"); long parsed = Long.parseLong(raw); require(parsed >= min && parsed <= max, 400, "分页参数超出范围"); return (int) parsed; }
}
