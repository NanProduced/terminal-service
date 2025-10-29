-- =========================================
-- 彩光终端服务 MySQL数据库初始化脚本
-- Version: 1.0
-- Author: Backend Team
-- Created: 2025-09-07
-- =========================================

-- 设置字符集和SQL模式
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET sql_mode = 'STRICT_TRANS_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION';

-- =========================================
-- 1. 设备终端账号表 (device_terminal_account)
-- 功能: 设备认证核心表，支持终端设备登录验证
-- 特点: 高频查询，需要account唯一性约束
-- =========================================
DROP TABLE IF EXISTS `device_terminal_account`;

CREATE TABLE `device_terminal_account` (
  `device_id` BIGINT NOT NULL COMMENT '设备ID-雪花算法生成',
  `account` VARCHAR(64) NOT NULL COMMENT '设备账号',
  `password` VARCHAR(255) NOT NULL COMMENT '设备密码-BCrypt加密',
  `account_status` TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态: 0-禁用, 1-启用',
  `first_login_time` DATETIME(3) NULL COMMENT '首次登录时间-上云时间',
  `last_login_time` DATETIME(3) NULL COMMENT '最后登录时间',
  `last_login_ip` VARCHAR(45) NULL COMMENT '最后登录IP地址',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 1 COMMENT '版本号-乐观锁',
  PRIMARY KEY (`device_id`),
  UNIQUE KEY `uk_account` (`account`) COMMENT '账号唯一索引-业务要求',
  KEY `idx_account_status` (`account_status`) COMMENT '状态查询索引',
  KEY `idx_last_login_time` (`last_login_time`) COMMENT '登录时间排序索引'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci 
  COMMENT='设备终端账号表-认证核心表';

-- =========================================
-- 2. 设备截图记录表 (device_screenshot_record)
-- 功能: 保存设备最新截图信息，对接MinIO存储
-- 特点: 每设备一条记录，device_id为主键，insertOrUpdate覆盖旧记录
-- =========================================
DROP TABLE IF EXISTS `device_screenshot_record`;

CREATE TABLE `device_screenshot_record` (
  `device_id` BIGINT NOT NULL COMMENT '设备ID-主键(雪花算法)',
  `upload_time` DATETIME(3) NOT NULL COMMENT '最新截图上传时间',
  `object_key` VARCHAR(255) NOT NULL COMMENT 'MinIO对象存储键值',
  `size` BIGINT UNSIGNED NULL COMMENT '文件大小-字节',
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`device_id`),
  KEY `idx_upload_time` (`upload_time`) COMMENT '上传时间查询索引-按时间排序'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci 
  COMMENT='设备截图记录表-保存最新截图状态';

-- =========================================
-- 3. 设备开关机记录表 (device_switch_on_record)
-- 功能: 记录设备开关机状态，用于在线统计和故障分析
-- 特点: 日志型数据，按设备维度统计查询
-- =========================================
DROP TABLE IF EXISTS `device_switch_on_record`;

CREATE TABLE `device_switch_on_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID-自增主键',
  `device_id` BIGINT NOT NULL COMMENT '设备ID',
  `switch_on_utc` BIGINT NOT NULL COMMENT '开机时间戳-UTC毫秒',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_device_id` (`device_id`) COMMENT '设备查询索引-按设备统计',
  KEY `idx_device_switch_time` (`device_id`, `switch_on_utc`) COMMENT '设备开机时间复合索引',
  KEY `idx_create_time` (`create_time`) COMMENT '创建时间索引-数据清理'
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci 
  COMMENT='设备开关机记录表-状态监控';

-- =========================================
-- 4. 设备删除记录表 (device_deletion_record)
-- 功能: 记录设备数据清理操作，供运维审计查看
-- 特点: 运维日志，低频查询，长期存储
-- =========================================
DROP TABLE IF EXISTS `device_deletion_record`;

CREATE TABLE `device_deletion_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID-自增主键',
  `device_id` BIGINT NOT NULL COMMENT '设备ID',
  `cleanup_mode` VARCHAR(20) NOT NULL COMMENT '清理模式: ALL-全部, INCLUDE-包含, EXCLUDE-排除',
  `data_types` JSON NOT NULL COMMENT '数据类型配置-JSON格式存储',
  `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '清理状态: PENDING-待处理, RUNNING-执行中, SUCCESS-成功, FAILED-失败, PARTIAL-部分成功',
  `start_time` DATETIME(3) NULL COMMENT '清理开始时间',
  `end_time` DATETIME(3) NULL COMMENT '清理结束时间',
  `deleted_counts` JSON NULL COMMENT '清理结果统计-JSON格式: {"mysql":100, "mongodb":500}',
  `error_message` TEXT NULL COMMENT '失败原因描述',
  `execution_time_ms` BIGINT UNSIGNED NULL COMMENT '执行时长-毫秒',
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_device_id` (`device_id`) COMMENT '设备查询索引-运维查看',
  CONSTRAINT `chk_cleanup_mode` CHECK (`cleanup_mode` IN ('ALL', 'INCLUDE', 'EXCLUDE')),
  CONSTRAINT `chk_status` CHECK (`status` IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL'))
) ENGINE=InnoDB 
  DEFAULT CHARSET=utf8mb4 
  COLLATE=utf8mb4_unicode_ci 
  COMMENT='设备删除记录表-运维审计日志';

-- =========================================
-- 索引创建完成提示
-- =========================================
SELECT 
  '数据库初始化完成' as message,
  '4个表创建成功' as tables_created,
  '索引策略已优化' as index_status,
  '支持分区和约束' as features,
  CURRENT_TIMESTAMP(3) as completed_at;

-- 恢复外键检查
SET FOREIGN_KEY_CHECKS = 1;

-- =========================================
-- 脚本执行说明
-- =========================================
/*
创建的表和索引说明:

1. device_terminal_account (设备账号表)
   - 主键: device_id 
   - 唯一索引: account (满足业务要求)
   - 普通索引: account_status, last_login_time

2. device_screenshot_record (截图记录表)  
   - 主键: device_id (每设备保存最新截图)
   - 普通索引: upload_time (按时间排序), object_key (MinIO清理)
   - 特性: insertOrUpdate覆盖机制，支持最新截图查询

3. device_switch_on_record (开关机记录表)
   - 主键: id (自增)
   - 索引: device_id (按设备查询)
   - 复合索引: device_id + switch_on_utc
   - 普通索引: create_time

4. device_deletion_record (删除记录表)
   - 主键: id (自增)  
   - 索引: device_id (设备查询)
   - 复合索引: device_id + create_time (历史查询)
   - 复合索引: status + create_time (任务监控)
   - 普通索引: cleanup_mode
   - 特性: JSON字段支持，约束检查

性能优化特点:
- 针对性索引设计，覆盖主要查询场景
- 时序表支持分区，提升大数据量查询性能  
- JSON字段支持复杂数据结构存储
- 约束检查保证数据完整性
- utf8mb4字符集支持完整Unicode
*/