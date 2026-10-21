-- ============================================================
-- 参数表敏感化 增量脚本
--
-- 已写入：base.sql 的「13、参数配置表」与「17、系统级参数」两节、
--         blog.sql 的「3、博客参数配置」「12、博客开放接口参数」两节、
--         meal.sql 的「App 版本发布令牌」一节
--         （新库跑模块脚本即可，不需要本文件）
-- 适用：只给已建好、不打算重跑模块脚本的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/config_sensitive_20261021.sql
-- 本增量执行结束可删除。
--
-- 背景：2026-10-21 渗透事件中，攻击者以 ry 账号调用
-- GET /system/config/configKey/{configKey} 读到了 R2、GitHub、开放接口、
-- 发布令牌等多份明文凭据。该接口此前没有任何权限注解，任意登录用户可读。
--
-- 修复分两层：
--   1) 接口层：getConfigKey 补 system:config:query，且敏感键只返回掩码；
--   2) 存储层：sensitive='Y' 的值加密入库（AES-GCM，主密钥 HOWE_SECRET_KEY）。
--
-- 注意：本脚本只做表结构变更与标记，**不搬运明文到密文**——
-- 加密需要主密钥，只能由应用侧完成。执行完本脚本后，请在
-- 「系统管理 > 参数设置」把下表列出的敏感项逐个重新保存一次
-- （留空提交表示不修改，所以必须重新输入明文）。
-- ============================================================

-- 1) 表结构：新增「是否敏感」标记
alter table sys_config
  add column `sensitive` char(1) default 'N' comment '是否敏感（Y是 N否）；Y 时值加密入库，HTTP 接口只返回掩码';

-- 1.1) 放宽值长度：密文 = enc:v1: 前缀 + Base64(IV 12 字节 + 密文 + 16 字节标签)，
--      比明文长约 40%。500 字符的密钥加密后会溢出，截断的密文永远解不开。
alter table sys_config
  modify column config_value varchar(2048) default '' comment '参数键值（敏感项为 enc:v1: 前缀的密文，密文比明文长约 40%）';

-- 1.2) 新增「参数分类」：自由文本，仅用于管理端按业务域分组。
--      与 config_type（系统内置 Y/N）不是一回事，故字段名用 param_type。
alter table sys_config
  add column param_type varchar(32) default '' comment '参数分类（自由文本，如：存储/博客/AI/点餐），仅用于管理端分组展示';

-- 2) 回填存量敏感键
--    这些键的值都是可直接冒用身份的凭据：R2 密钥、GitHub PAT、webhook 密钥、
--    开放接口令牌、App 发布令牌、Turnstile 服务端密钥。
update sys_config set `sensitive` = 'Y'
where config_key in (
    'sys.storage.r2.secretAccessKey',
    'blog.github.token',
    'blog.github.webhookSecret',
    'blog.open.token',
    'meal.app.publishToken',
    'sys.turnstile.secretKey'
);

-- 3) 回填参数分类（按业务域，便于参数页分组）
--    只填空值，不覆盖运维已手工维护的分类。
update sys_config set param_type = '基础' where param_type = '' and config_key like 'sys.index.%';
update sys_config set param_type = '账号' where param_type = '' and config_key like 'sys.account.%';
update sys_config set param_type = '账号' where param_type = '' and config_key in ('sys.user.initPassword', 'sys.login.blackIPList');
update sys_config set param_type = '人机校验' where param_type = '' and config_key like 'sys.turnstile.%';
update sys_config set param_type = '存储' where param_type = '' and config_key like 'sys.storage.%';
update sys_config set param_type = '博客' where param_type = '' and config_key like 'blog.%';
update sys_config set param_type = 'AI' where param_type = '' and config_key like 'ai.%';
update sys_config set param_type = '点餐' where param_type = '' and config_key like 'meal.%';
update sys_config set param_type = '文件分享' where param_type = '' and config_key like 'share.%';
update sys_config set param_type = '首页' where param_type = '' and config_key like 'sys.home.%';
-- 兜底：仍未归类的统一进「其它」，避免下拉里出现空选项
update sys_config set param_type = '其它' where param_type = '';

-- 4) 核对结果（执行后应看到 6 行，且 config_value 仍为明文）
--    select config_id, config_key, `sensitive`, char_length(config_value) as value_len
--    from sys_config where `sensitive` = 'Y' order by config_id;

-- 5) 收尾：清空参数缓存，让应用按新表结构重新加载
--    （也可以在「系统管理 > 参数设置」点「刷新缓存」）
--    redis-cli --scan --pattern "sys_config:*" | xargs -r redis-cli del
