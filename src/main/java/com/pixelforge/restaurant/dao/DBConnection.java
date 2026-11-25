package com.pixelforge.restaurant.dao;

/*
 * =============================================================================
 * DBConnection.java - Singleton JDBC Connection Manager
 * =============================================================================
 *
 * WHAT IS A SINGLETON PATTERN?
 * ----------------------------
 * The Singleton design pattern ensures that a class has ONLY ONE instance
 * throughout the entire application lifetime. This is very useful for managing
 * database connections because:
 *
 *   1. Creating a new DB connection every time is EXPENSIVE (slow, memory-heavy).
 *   2. Having too many open connections can crash the database server.
 *   3. A single shared connection is efficient and easy to manage.
 *
 * HOW SINGLETON WORKS HERE:
 * -------------------------
 * - We store the single Connection object in a 'static' variable.
 * - The first call to getConnection() creates the connection.
 * - Every subsequent call checks: "Is the connection already open?"
 *   - YES → Return the existing connection (do NOT create a new one).
 *   - NO  → Create a new connection, store it, and return it.
 *
 * IMPORTANT: This is a simple (non-thread-safe) singleton suitable for
 * single-threaded or lightly concurrent desktop apps. For production
 * multi-threaded servers, use a connection pool (e.g., HikariCP).
 *
 * =============================================================================
 */

import java.sql.Connection; // Represents the database connection
import java.sql.DriverManager; // Factory class that creates Connection objects
import java.sql.SQLException; // Thrown when database operations fail

/**
 * DBConnection - Singleton class that manages a single JDBC Connection
 * to the PixelForge Restaurant MySQL database.
 *
 * Usage:
 * Connection conn = DBConnection.getConnection();
 * // ... perform DB operations ...
 * DBConnection.closeConnection();
 */
public class DBConnection {

    // -------------------------------------------------------------------------
    // DATABASE CONFIGURATION CONSTANTS
    // These are defined as constants (static final) so they are easy to change
    // in one place if the database credentials ever change.
    // -------------------------------------------------------------------------

    /**
     * JDBC URL for MySQL. Breaking it down:
     * - jdbc:mysql:// → Driver type
     * - localhost:3306 → Database host and port (3306 is MySQL's default)
     * - pixelforge_restaurant → Database (schema) name
     * - useSSL=false → Disable SSL for local dev (avoid certificate warnings)
     * - serverTimezone=Asia/Kolkata → Tell MySQL the server is in Indian timezone
     */
    private static final String URL = "jdbc:mysql://localhost:3306/pixelforge_restaurant?useSSL=false&serverTimezone=Asia/Kolkata";

    /**
     * MySQL username - default 'root', configurable via System property 'db.user'
     * or env var 'DB_USER'
     */
    private static final String USER = System.getProperty("db.user",
            System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "root");

    /**
     * MySQL password - default empty string for XAMPP/WAMP, configurable via System
     * property 'db.password' or env var 'DB_PASSWORD'
     */
    private static final String PASS = System.getProperty("piyush@2007",
            System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "");

    // -------------------------------------------------------------------------
    // THE SINGLE SHARED CONNECTION INSTANCE
    // 'static' means this variable belongs to the CLASS, not to any object.
    // There will only ever be one of these across the whole application.
    // -------------------------------------------------------------------------

    /** The one and only Connection instance for this application */
    private static Connection connection = null;

    // -------------------------------------------------------------------------
    // PRIVATE CONSTRUCTOR
    // Making the constructor private means: no other class can do "new
    // DBConnection()"
    // This FORCES everyone to use getConnection() instead, maintaining the
    // singleton.
    // -------------------------------------------------------------------------

    private DBConnection() {
        // Private constructor - prevents instantiation from outside this class
    }

    // -------------------------------------------------------------------------
    // getConnection() - The main entry point for getting a DB connection
    // -------------------------------------------------------------------------

    /**
     * Returns the single shared database Connection.
     *
     * If the connection has never been created, or if it was closed/lost,
     * this method creates a fresh one using DriverManager.
     *
     * @return Connection object ready for SQL operations
     * @throws SQLException if the database cannot be reached or credentials are
     *                      wrong
     */
    public static Connection getConnection() throws SQLException {
        // Check if we already have a valid, open connection
        // isClosed() returns true if the connection has been shut down
        if (connection == null || connection.isClosed()) {
            // No valid connection exists - create a new one
            System.out.println("[DBConnection] Creating new database connection...");

            // DriverManager.getConnection() is the JDBC factory method.
            // It reads the URL to figure out which driver to use (MySQL here),
            // then opens a TCP connection to the database server and authenticates.
            connection = DriverManager.getConnection(URL, USER, PASS);

            System.out.println("[DBConnection] Connection established successfully.");
        }
        // Return the existing (or newly created) connection
        return connection;
    }

    // -------------------------------------------------------------------------
    // closeConnection() - Cleanly shut down the connection
    // -------------------------------------------------------------------------

    /**
     * Closes the database connection and sets the reference to null.
     *
     * Always close the connection when the application is shutting down
     * to free up server-side resources (sockets, threads, memory).
     */
    public static void closeConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close(); // Release the connection back to MySQL
                    System.out.println("[DBConnection] Connection closed successfully.");
                }
            } catch (SQLException e) {
                // Log the error but don't crash - closing is best-effort
                System.err.println("[DBConnection] Error while closing connection: " + e.getMessage());
            } finally {
                // Set to null so the next getConnection() call creates a fresh one
                connection = null;
            }
        }
    }
}
