-- ============================================================
-- 家庭邀请码 增量脚本
--
-- 已写入：base.sql 的「部门表」与「初始化-部门表数据」两节
--         （新库跑 base.sql 即可，不需要本文件）
-- 适用：只给已建好、不打算重跑 base.sql 的库打补丁
-- 执行：mysql -u root -p howe-system < 增量/dept_family_code_20261021.sql
-- 本增量执行结束可删除。
--
-- 背景：2026-10-21 渗透事件暴露出家庭注册的两个问题：
--   1) 家庭代码直接用了 dept_id，自增主键可枚举（100~109 几十个候选）；
--   2) registerFamily 只校验部门存在且启用，没校验「是不是家庭」，
--      攻击者传 familyCode=100（根部门）就能注册进平台组织架构。
--
-- 修复：
--   - 邀请码改存 Redis（family_code:{码} → deptId，TTL 1 天），不入库；
--   - 部门表加 dept_type 区分「组织部门 D / 家庭 F」，只有 F 能发码、能被注册加入。
--
-- 注意：本脚本不创建邀请码——邀请码由家庭管理员在点餐端「家庭与成员」页生成。
-- ============================================================

-- 1) 部门类型：D 组织部门 / F 家庭
alter table sys_dept
  add column dept_type char(1) default 'D' comment '部门类型（D组织部门 F家庭）；只有 F 可以生成家庭邀请码、被家庭注册加入';

-- 2) 回填家庭类型
--    判定依据：该部门下存在持有家庭角色（meal_eater / meal_chef / meal_manager）的用户。
--    这比「parent_id=0」可靠——本地与生产都存在挂在 parent_id=0 下的平台根部门。
update sys_dept d
set d.dept_type = 'F'
where exists (
    select 1 from sys_user u
    join sys_user_role ur on ur.user_id = u.user_id
    join sys_role r on r.role_id = ur.role_id
    where u.dept_id = d.dept_id
      and u.del_flag = '0'
      and r.role_key in ('meal_eater', 'meal_chef', 'meal_manager')
);

-- 3) 核对：应看到家庭部门列表；其余都是 D
--    select dept_id, dept_name, parent_id, dept_type from sys_dept order by dept_id;

-- 4) 家庭部门如果还有父节点，把它们修正为顶级
--    （家庭是独立实体，不该挂在组织架构下面；本地/生产的家庭都是 parent_id=0）
--    update sys_dept set parent_id = 0, ancestors = '0'
--    where dept_type = 'F' and parent_id <> 0;

-- 5) 清理历史上可能残留的「家庭代码」类参数（如果有）
--    旧实现没有把家庭代码存参数表，这一步通常无影响。
--    delete from sys_config where config_key like 'meal.family.%';
