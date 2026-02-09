package com.example.timetablegenerator.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String URL  = "jdbc:postgresql://localhost:5432/timetable_db";
    private static final String USER = "postgres";
    private static final String PASS = "kitcoek";

    public static Connection getConnection() {
        try {
            // The driver will auto-register because the dependency is on classpath,
            // but Class.forName is harmless if you want to keep it.
            Class.forName("org.postgresql.Driver");

            Connection conn = DriverManager.getConnection(URL, USER, PASS);
            conn.setAutoCommit(true);

            // Lightweight sanity log (printed once per connection)
            System.out.println("✅ Connected to PostgreSQL: " + URL + " as " + USER);
            return conn;
        } catch (Exception e) {
            // This will bubble up to your service and print "Skipping DB save ..."
            throw new RuntimeException("Cannot obtain DB connection for URL=" + URL, e);
        }
    }
}
