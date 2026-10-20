package com.hc.drown.module.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hc.drown.module.admin.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
