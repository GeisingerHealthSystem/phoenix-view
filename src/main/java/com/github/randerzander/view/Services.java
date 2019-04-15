package com.github.randerzander.view;

import org.apache.ambari.view.ViewContext;

import org.apache.log4j.Logger;
import org.json.JSONObject;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.ArrayList;
import java.sql.*;
import org.apache.phoenix.jdbc.PhoenixDriver;

import java.util.Map;
import java.util.Properties;

public class Services extends HttpServlet {
    private ViewContext viewContext;
    private Connection connection;
    private Properties aProperties;
    final Logger logger = Logger.getRootLogger();

    @Override
    public void init(ServletConfig config) throws ServletException {
      super.init(config);

      ServletContext context = config.getServletContext();
//      viewContext = (ViewContext) context.getAttribute(ViewContext.CONTEXT_ATTRIBUTE);
       aProperties = new Properties();
       try{
         aProperties.load(new FileInputStream("phoenix-view.properties"));
       }catch (IOException e){
         aProperties.setProperty("jdbc.url","gdchdpmn02drlx.geisinger.edu:2181:/hbase-secure");
         aProperties.setProperty("phoenix.user","cdis_sys_dev");
         aProperties.setProperty("phoenix.password","Swukad&KAJesw5Dr");
         logger.warn("File Not found : Using default values");
       }
      System.err.println("Trying to start view!");
      try {
        Class.forName("org.apache.phoenix.jdbc.PhoenixDriver");
      } catch (ClassNotFoundException ex) {
        System.out.println("Error: unable to load driver class!");
        System.exit(1);
      }
      try {
        connection = DriverManager.getConnection(aProperties.getProperty("jdbc.url"), aProperties.getProperty("phoenix.user"), aProperties.getProperty("phoenix.password"));
      } catch (SQLException e) { e.printStackTrace(); }
    }

    private String concatWS(String separator, String[] tokens){
      StringBuilder ret = new StringBuilder();
      for (String token : tokens){
        if (ret.equals("")) ret.append(token);
        else {
          ret.append(separator);
          ret.append(token);
        }
      }
      return ret.toString();
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
      //Read request body and build Hive query
      BufferedReader reader = request.getReader();
      StringBuilder builder = new StringBuilder();
      String line;
      while( (line = reader.readLine()) != null){ builder.append(line); }
      String query = builder.toString();

      JSONObject json = new JSONObject();
      json.put("query", query);

      //JDBC driver doesn't like semicolons -- remove it if necessary
      if (query.length() > 0 && query.charAt(query.length()-1)==';') {
        query = query.substring(0, query.length()-1);
      }

      //Run the query and build the response
      PrintWriter writer = response.getWriter();
      ArrayList<String[]> rows = new ArrayList<String[]>();
      try {
        ResultSet res = connection.createStatement().executeQuery(query);
        
        //Write column headers
        ResultSetMetaData rms = res.getMetaData();
        String[] cols = new String[rms.getColumnCount()];
        for (int i=0; i < cols.length; i++){ cols[i] = rms.getColumnName(i+1);}
        json.put("columns", cols);

        //Write each row from the result set
        while (res.next()) {
          String[] row = new String[cols.length];
          for (int i=0; i < cols.length; i++){ row[i] = res.getString(i+1); }
          rows.add(row);
        }
      }catch (SQLException e) {
        json.put("columns", new String[]{"Error"});
        rows.add(new String[]{e.toString()});
      }
      json.put("result", rows);
      writer.println(json.toString());
    }
}
