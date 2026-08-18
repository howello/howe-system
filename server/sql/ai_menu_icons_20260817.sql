-- 管理员助手菜单图标增量：可重复执行，不硬编码 menu_id
UPDATE sys_menu
SET icon = 'ai-assistant'
WHERE parent_id = 0 AND path = 'ai';

SET @ai_parent := NULL;
SELECT @ai_parent := menu_id
FROM sys_menu
WHERE parent_id = 0 AND path = 'ai'
LIMIT 1;

UPDATE sys_menu
SET icon = 'ai-agent'
WHERE parent_id = @ai_parent AND path = 'agent';