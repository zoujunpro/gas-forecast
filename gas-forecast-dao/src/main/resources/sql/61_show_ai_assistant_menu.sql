use gas_data;

update sys_permission_tb
set parent_id = null,
    permission_name = 'AI智能问答',
    path = '/ai-assistant',
    component = 'views/ai/AiAssistant.vue',
    perms = 'ai:assistant:use',
    icon = 'MagicStick',
    sort_no = 1,
    hidden = 0,
    status = 1
where id = 14;
