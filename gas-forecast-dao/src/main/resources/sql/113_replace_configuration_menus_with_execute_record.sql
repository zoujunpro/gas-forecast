-- 模型管理只保留配置与执行结果入口；训练/预测配置不再作为人工维护菜单暴露。
-- 复用原“预测配置”菜单ID，避免已有角色丢失菜单授权。
START TRANSACTION;

DELETE role_permission
FROM sys_role_permission_ref role_permission
JOIN sys_permission_tb permission ON permission.id = role_permission.permission_id
WHERE permission.id = 64 OR permission.parent_id IN (64, 65);

DELETE FROM sys_permission_tb WHERE parent_id IN (64, 65);
DELETE FROM sys_permission_tb WHERE id = 64;

UPDATE sys_permission_tb
SET permission_name = '执行记录',
    path = '/model/execute-records',
    component = 'views/model/ModelExecuteRecordManagement.vue',
    perms = 'model:execute-record:list',
    sort_no = 65,
    hidden = 0,
    status = 1
WHERE id = 65;

COMMIT;
