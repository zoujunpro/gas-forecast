use gas_data;

alter table execution_task_detail_tb drop index uk_task_detail_seq;
alter table execution_task_detail_tb drop column detail_seq;
