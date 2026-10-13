use gas_data;

insert ignore into sys_permission_tb
    (id, parent_id, permission_name, path, component, permission_type, perms, icon, sort_no, hidden, status)
values
    (14, null, 'AI智能问答', '/ai-assistant', 'views/ai/AiAssistant.vue', 'MENU', 'ai:assistant:use', 'MagicStick', 10, 1, 1);

update sys_permission_tb
set parent_id = null, permission_name = 'AI智能问答', path = '/ai-assistant', component = 'views/ai/AiAssistant.vue',
    perms = 'ai:assistant:use', icon = 'MagicStick', sort_no = 10, hidden = 1, status = 1
where id = 14;

insert ignore into sys_role_permission_ref (role_id, permission_id)
select role.id, 14
from sys_role_tb role
where role.role_code = 'admin';
