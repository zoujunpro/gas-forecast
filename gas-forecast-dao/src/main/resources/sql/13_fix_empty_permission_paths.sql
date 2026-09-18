use gas_data;

update sys_permission_tb
set path = null
where path = '';

update sys_permission_tb
set component = null
where component = '';

update sys_permission_tb
set perms = null
where perms = '';

update sys_permission_tb
set button_code = null
where button_code = '';

update sys_permission_tb
set icon = null
where icon = '';
