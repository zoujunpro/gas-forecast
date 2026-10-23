import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** Aligns empty task-detail and step tables to business-number associations. */
public class MigrateExecutionStepScopeSql {
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/gas_data?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
    public static void main(String[] args) throws Exception {
        try (Connection c = DriverManager.getConnection(URL, "root", "mysql2026"); Statement s = c.createStatement()) {
            if (column(s, "execution_task_detail_tb", "task_id")) {
                dropIndex(s, "execution_task_detail_tb", "uk_task_detail_seq"); dropIndex(s, "execution_task_detail_tb", "uk_task_target");
                s.execute("alter table execution_task_detail_tb drop column task_id");
                s.execute("alter table execution_task_detail_tb add unique key uk_task_detail_seq (task_no, detail_seq), add unique key uk_task_target (task_no, dimension_type, region_code, industry_code, customer_code)");
            }
            if (column(s, "execution_step_tb", "task_no")) {
                dropIndex(s, "execution_step_tb", "uk_task_attempt_step"); dropIndex(s, "execution_step_tb", "idx_task_order"); dropIndex(s, "execution_step_tb", "idx_task_status");
                s.execute("alter table execution_step_tb drop column task_no");
            }
            if (!column(s, "execution_step_tb", "task_detail_no"))
                s.execute("alter table execution_step_tb add column task_detail_no varchar(96) not null comment '关联execution_task_detail_tb.detail_no' after step_no, add column duration_ms bigint default null comment '真实耗时毫秒' after finished_at");
            if (!column(s, "execution_step_tb", "attempt_no"))
                s.execute("alter table execution_step_tb add column attempt_no int not null default 1 comment '执行尝试次数' after task_detail_no");
            if (!column(s, "execution_step_tb", "metrics_json"))
                s.execute("alter table execution_step_tb add column metrics_json mediumtext default null comment '步骤关键指标，可保存文本或JSON' after output_json");
            if (!index(s, "execution_step_tb", "uk_detail_attempt_step"))
                s.execute("alter table execution_step_tb add unique key uk_detail_attempt_step (task_detail_no, attempt_no, step_code), add key idx_detail_order (task_detail_no, attempt_no, step_order), add key idx_detail_status (task_detail_no, status)");
            System.out.println("execution task detail and step schema ready");
        }
    }
    private static boolean column(Statement s, String t, String n) throws Exception { return exists(s, "columns", "table_name='"+t+"' and column_name='"+n+"'"); }
    private static boolean index(Statement s, String t, String n) throws Exception { return exists(s, "statistics", "table_name='"+t+"' and index_name='"+n+"'"); }
    private static void dropIndex(Statement s, String t, String n) throws Exception { if (index(s,t,n)) s.execute("alter table "+t+" drop index "+n); }
    private static boolean exists(Statement s, String kind, String where) throws Exception { try (ResultSet r=s.executeQuery("select 1 from information_schema."+kind+" where table_schema=database() and "+where+" limit 1")) { return r.next(); } }
}
