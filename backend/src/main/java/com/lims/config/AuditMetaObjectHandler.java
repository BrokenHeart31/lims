package com.lims.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.lims.security.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计四字段自动填充（AGENTS 6.1）：created_by/created_at/updated_by/updated_at。
 * created_by/updated_by 取当前登录人工号；匿名场景（如迁移/种子）填 system。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private static final String ANONYMOUS = "system";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String operator = SecurityUtils.getUsername().orElse(ANONYMOUS);
        this.strictInsertFill(metaObject, "createdBy", String.class, operator);
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedBy", String.class, operator);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedBy", String.class,
                SecurityUtils.getUsername().orElse(ANONYMOUS));
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
