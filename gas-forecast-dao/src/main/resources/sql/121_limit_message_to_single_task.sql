use gas_data;

-- 清理任务表重建后遗留的无效消息引用。
delete reference
from ai_message_ref_tb reference
left join execution_task_tb task on task.task_no = reference.task_no
where task.id is null;

-- 当前业务规定一条消息最多关联一个任务；任务下可包含多个detail_no明细。
alter table ai_message_ref_tb
    drop index uk_message_task_relation,
    add unique key uk_message_id (message_id);
