package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DataBaseConnect {
    String url = "jdbc:mysql://localhost:3306/";
    String db = "smpp_protocol_sms";
    String user = "root";
    String pass = "1234";
    Statement st;

    public DataBaseConnect() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection con = DriverManager.getConnection(url + db, user, pass);
            st = con.createStatement();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void insertInDB(String tableName, String sms, String date) throws SQLException {
        st.execute("INSERT " + tableName + "(sms_text, sms_date) VALUES('"+ sms + "', '" + date + "');");
    }

}
