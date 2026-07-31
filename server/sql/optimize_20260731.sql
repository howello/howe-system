-- ----------------------------
-- server 优化：验证码改造、Cloudflare 人机校验、博客开放发布接口
-- 执行后若服务已在运行，需到「系统管理 > 参数设置」点一次「刷新缓存」才会生效
-- ----------------------------

-- ----------------------------
-- 1、验证码
-- 原先验证码类型写死在 application.yml 的 howe.captchaType 里，改配置要重启容器。
-- 下沉到参数配置表后可随时切换；留空则仍回落到 yml 的值，老部署不受影响。
-- 注意 sys.account.captchaEnabled 在 ry_20260417.sql 里已经有了，这里不重复插入。
-- ----------------------------
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('账号自助-验证码类型', 'sys.account.captchaType', 'math', 'Y', 'admin', sysdate(), '', null, 'char 扭曲字符 / math 算术运算 / line 线段干扰 / circle 圆圈干扰 / shear 扭曲干扰 / gif 动态图形 / random 每次随机');

-- ----------------------------
-- 2、Cloudflare Turnstile 真人校验
-- 与图形验证码是叠加关系而非替代，两个开关互相独立。
-- 开关打开但密钥没配时后端一律拒绝登录，不会静默放行。
-- 密钥在 https://dash.cloudflare.com/?to=/:account/turnstile 申请
-- ----------------------------
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('人机校验-开关', 'sys.turnstile.enabled', 'false', 'Y', 'admin', sysdate(), '', null, '是否启用 Cloudflare Turnstile 真人校验，登录与注册均生效');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('人机校验-站点密钥', 'sys.turnstile.siteKey', '', 'Y', 'admin', sysdate(), '', null, 'Site Key，会随 /captchaImage 下发给前端渲染组件，可公开');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('人机校验-服务端密钥', 'sys.turnstile.secretKey', '', 'Y', 'admin', sysdate(), '', null, 'Secret Key，只在后端校验时使用，切勿下发前端');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('人机校验-校验地址', 'sys.turnstile.verifyUrl', 'https://challenges.cloudflare.com/turnstile/v0/siteverify', 'Y', 'admin', sysdate(), '', null, 'Cloudflare 官方校验端点，一般不用改');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('人机校验-超时时间', 'sys.turnstile.timeout', '5000', 'Y', 'admin', sysdate(), '', null, '调用 Cloudflare 校验接口的超时毫秒数，超时按校验失败处理');

-- ----------------------------
-- 3、博客开放发布接口
-- POST /blog/open/article，匿名接口，靠请求头 X-Blog-Token 与下面的令牌比对放行。
-- 开关关闭或令牌为空时一律拒绝，不存在裸奔的写入口。
-- ----------------------------
insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-开放接口开关', 'blog.open.enabled', 'false', 'Y', 'admin', sysdate(), '', null, '是否允许站外工具调用 POST /blog/open/article 投稿');

insert into sys_config (config_name, config_key, config_value, config_type, create_by, create_time, update_by, update_time, remark)
values('博客-开放接口令牌', 'blog.open.token', '', 'Y', 'admin', sysdate(), '', null, '调用方需在请求头 X-Blog-Token 中带上该值。建议 32 位以上随机串，留空等同于关闭');
