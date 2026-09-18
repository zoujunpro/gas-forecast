use gas_data;

insert into sys_code_sequence_tb (code_type, current_value) values ('REG', 0)
on duplicate key update code_type = values(code_type);

delete from base_region_tb
where region_name in ('北京', '天津', '山东', '山西', '河北', '河南', '陕西')
  and region_code not regexp '^REG[0-9]{6}$';

insert into base_region_tb (
    region_code,
    region_name,
    remark,
    created_by,
    created_at,
    updated_by,
    updated_at,
    updated_by_name,
    created_by_name
) values
    ('REG000005', '北京', 'winner-agent模拟省份', 'zoujun', now(), 'zoujun', now(), '邹军', '邹军'),
    ('REG000006', '天津', 'winner-agent模拟省份', 'zoujun', now(), 'zoujun', now(), '邹军', '邹军'),
    ('REG000007', '山东', 'winner-agent模拟省份', 'zoujun', now(), 'zoujun', now(), '邹军', '邹军'),
    ('REG000008', '山西', 'winner-agent模拟省份', 'zoujun', now(), 'zoujun', now(), '邹军', '邹军'),
    ('REG000009', '河北', 'winner-agent模拟省份', 'zoujun', now(), 'zoujun', now(), '邹军', '邹军'),
    ('REG000010', '河南', 'winner-agent模拟省份', 'zoujun', now(), 'zoujun', now(), '邹军', '邹军'),
    ('REG000011', '陕西', 'winner-agent模拟省份', 'zoujun', now(), 'zoujun', now(), '邹军', '邹军')
on duplicate key update
    region_name = values(region_name),
    remark = values(remark),
    updated_by = values(updated_by),
    updated_by_name = values(updated_by_name),
    updated_at = values(updated_at);

update sys_code_sequence_tb
set current_value = greatest(current_value, 11)
where code_type = 'REG';
