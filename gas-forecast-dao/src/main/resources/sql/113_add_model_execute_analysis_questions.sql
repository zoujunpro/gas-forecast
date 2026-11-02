alter table model_execute_record_tb
    add column analysis_questions_json mediumtext default null comment '基于本次AI研判生成的推荐追问，JSON数组'
    after analysis_content;
