use gas_data;

-- 训练过程统一记录在 execution_step_tb，不再维护重复的模型训练步骤表。
drop table if exists model_train_execution_step_tb;
