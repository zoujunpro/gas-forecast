import java.sql.*;
public class ShowExecutionStepSchema {
  public static void main(String[] a) throws Exception {
    try (Connection c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/gas_data?useSSL=false&allowPublicKeyRetrieval=true","root","mysql2026"); Statement s=c.createStatement(); ResultSet r=s.executeQuery("show columns from execution_step_tb")) {
      while(r.next()) System.out.println(r.getString("Field")+" "+r.getString("Type"));
    }
  }
}
