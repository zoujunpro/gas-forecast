-- Remove the legacy forecast result and training batch pages from existing installations.

delete from sys_role_permission_ref
where permission_id in (
    select id from sys_permission_tb
    where id in (47, 48) or path in ('/data/forecast-results', '/data/train-batches')
);

delete from sys_department_permission_ref
where permission_id in (
    select id from sys_permission_tb
    where id in (47, 48) or path in ('/data/forecast-results', '/data/train-batches')
);

delete from sys_permission_tb
where id in (47, 48) or path in ('/data/forecast-results', '/data/train-batches');
