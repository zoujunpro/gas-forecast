use gas_data;

alter table model_train_config_tb
    add column model_code varchar(64) default null comment '所属模型编码' after agent_code,
    add column model_name varchar(128) default null comment '所属模型名称' after model_code,
    add key idx_train_config_model (model_code);

update model_train_config_tb train
join model_config_tb model
  on train.agent_code = model.agent_code
set train.model_code = model.model_code,
    train.model_name = model.model_name
where train.model_code is null or train.model_code = '';
