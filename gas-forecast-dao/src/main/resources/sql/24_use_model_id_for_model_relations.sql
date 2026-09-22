-- 模型编码允许用户修改，模型相关关系改为通过稳定的模型ID关联。
alter table model_config_scope_tb
    add column model_id bigint null after id;

update model_config_scope_tb scope
    join model_config_tb model on scope.model_code = model.model_code
set scope.model_id = model.id
where scope.model_id is null;

alter table model_train_config_tb
    add column model_id bigint null after agent_code;

update model_train_config_tb train
    join model_config_tb model on train.model_code = model.model_code
set train.model_id = model.id,
    train.model_code = model.model_code,
    train.model_name = model.model_name
where train.model_id is null;

create index idx_model_config_scope_model_id on model_config_scope_tb (model_id);
create index idx_model_train_config_model_id on model_train_config_tb (model_id);
