package com.pixelforge.restaurant.dao;

/*
 * =============================================================================
 * TableDAO.java - Data Access Object for restaurant_tables
 * =============================================================================
 *
 * This DAO manages the restaurant_tables table, which tracks:
 *   - Which tables exist in the restaurant
 *   - Each table's seating capacity
 *   - Current status: 'AVAILABLE' or 'OCCUPIED'
 *   - When a table became occupied (occupied_since timestamp)
 *
 * SPECIAL DESIGN NOTE - External Connection Parameter:
 * ----------------------------------------------------
 * Some methods in this DAO accept a 'Connection conn' parameter from outside.
 * This is intentional! It allows the calling code (e.g., BillDAO) to pass in
 * a Connection that is part of a larger TRANSACTION.
 *
 * Example flow:
 *   BillDAO.saveBillAndFreeTable() opens a transaction:
 *     → Saves the bill (INSERT into bills)
 *     → Calls TableDAO.updateTableStatus() with the SAME connection
 *     → Commits the transaction
 *   If either step fails, BOTH are rolled back together (atomicity).
 *
 * This "shared connection" approach is a classic JDBC transaction pattern.
 *
 * =============================================================================
 */

import com.pixelforge.restaurant.model.RestaurantTable; // Model for a restaurant table

import java.sql.Connection;         // Database connection
import java.sql.PreparedStatement;  // Parameterized SQL query
import java.sql.ResultSet;          // Results from SELECT queries
import java.sql.SQLException;       // Database error
import java.sql.Timestamp;          // For reading TIMESTAMP columns from MySQL
import java.util.ArrayList;         // Dynamic list
import java.util.List;              // List interface

/**
 * TableDAO - Handles all database operations for the restaurant_tables table.
 *
 * Table schema:
 *   table_id       INT PRIMARY KEY
 *   capacity       INT NOT NULL
 *   status         ENUM('AVAILABLE', 'OCCUPIED') DEFAULT 'AVAILABLE'
 *   occupied_since TIMESTAMP NULL
 */
public class TableDAO {

    // =========================================================================
    // READ: Get all tables
    // =========================================================================

    /**
     * Fetches all restaurant tables from the database, ordered by table_id.
     *
     * SQL:  SELECT table_id, capacity, status, occupied_since FROM restaurant_tables ORDER BY table_id
     *
     * @return List of RestaurantTable objects representing every table in the restaurant
     * @throws SQLException if the database query fails
     */
    public List<RestaurantTable> getAllTables() throws SQLException {
        List<RestaurantTable> tables = new ArrayList<>();

        String sql = "SELECT table_id, capacity, status, occupied_since FROM restaurant_tables ORDER BY table_id";

        // try-with-resources: automatically closes all 3 resources when done
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            // Iterate over every row returned
            while (rs.next()) {
                // Read TIMESTAMP column safely and convert to LocalDateTime
                Timestamp occupiedSinceTs = rs.getTimestamp("occupied_since");
                java.time.LocalDateTime occupiedSince = (occupiedSinceTs != null) ? occupiedSinceTs.toLocalDateTime() : null;

                // Build a RestaurantTable object from each row's data
                RestaurantTable table = new RestaurantTable(
                        rs.getInt("table_id"),        // e.g., 1, 2, 3, ...
                        rs.getInt("capacity"),         // e.g., 2, 4, 6, 8
                        rs.getString("status"),        // "AVAILABLE" or "OCCUPIED"
                        occupiedSince                 // May be null if table is free
                );
                tables.add(table);
            }
        }

        return tables;
    }

    // =========================================================================
    // READ: Get a single table by ID
    // =========================================================================

    /**
     * Fetches a specific restaurant table by its primary key (table_id).
     *
     * SQL:  SELECT table_id, capacity, status, occupied_since FROM restaurant_tables WHERE table_id = ?
     *
     * @param tableId The primary key of the table to look up
     * @return The matching RestaurantTable, or null if not found
     * @throws SQLException if the query fails
     */
    public RestaurantTable getTableById(int tableId) throws SQLException {
        String sql = "SELECT table_id, capacity, status, occupied_since FROM restaurant_tables WHERE table_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Bind the table_id to the first ? placeholder
            pstmt.setInt(1, tableId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Timestamp occupiedSinceTs = rs.getTimestamp("occupied_since");
                    java.time.LocalDateTime occupiedSince = (occupiedSinceTs != null) ? occupiedSinceTs.toLocalDateTime() : null;

                    // Table found - return it
                    return new RestaurantTable(
                            rs.getInt("table_id"),
                            rs.getInt("capacity"),
                            rs.getString("status"),
                            occupiedSince
                    );
                }
            }
        }

        // No table found with that ID
        return null;
    }

    // =========================================================================
    // UPDATE: Change a table's status (uses EXTERNAL connection for transactions)
    // =========================================================================

    /**
     * Updates a table's status using a provided external Connection.
     *
     * WHY ACCEPT AN EXTERNAL CONNECTION?
     * This method is called by BillDAO as part of a multi-step transaction.
     * BillDAO controls the transaction (begin, commit, rollback), and passes
     * its connection here so this UPDATE is part of that same transaction.
     * If we opened our own connection here, the UPDATE would be in a SEPARATE
     * transaction and couldn't be rolled back together with the bill INSERT.
     *
     * SQL:  UPDATE restaurant_tables SET status=?, occupied_since=NULL WHERE table_id=?
     *
     * @param tableId The ID of the table to update
     * @param status  The new status: "AVAILABLE" or "OCCUPIED"
     * @param conn    The external Connection (managed by the caller's transaction)
     * @throws SQLException if the update fails
     */
    public void updateTableStatus(int tableId, String status, Connection conn) throws SQLException {
        // When freeing a table (setting to AVAILABLE), clear the occupied_since timestamp
        // We set occupied_since = NULL because the table is no longer occupied
        String sql = "UPDATE restaurant_tables SET status = ?, occupied_since = NULL WHERE table_id = ?";

        // NOTE: We do NOT use try-with-resources for conn here!
        // The Connection was given to us by the caller - they own it and will close it.
        // If we closed it here, the caller's transaction would break.
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);    // 1st ? = new status ("AVAILABLE")
            pstmt.setInt(2, tableId);      // 2nd ? = which table to update

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new SQLException("Table with ID " + tableId + " not found for status update.");
            }

            System.out.println("[TableDAO] Table " + tableId + " status updated to: " + status);
        }
        // The PreparedStatement is closed here, but conn is left open for the caller
    }

    // =========================================================================
    // UPDATE: Mark a table as OCCUPIED with current timestamp
    // =========================================================================

    /**
     * Sets a table's status to 'OCCUPIED' and records the current time as
     * the occupied_since timestamp (used by TableTimerMonitor for alerts).
     *
     * SQL:  UPDATE restaurant_tables SET status='OCCUPIED', occupied_since=NOW() WHERE table_id=?
     *
     * This method also uses an external Connection so it can participate in
     * a transaction if the caller needs it.
     *
     * @param tableId The ID of the table being occupied
     * @param conn    External Connection (managed by the caller)
     * @throws SQLException if the update fails
     */
    public void updateTableStatusOccupied(int tableId, Connection conn) throws SQLException {
        // NOW() is a MySQL function that returns the current date and time.
        // We store it so we can later calculate how long the table has been occupied.
        String sql = "UPDATE restaurant_tables SET status = 'OCCUPIED', occupied_since = NOW() WHERE table_id = ?";

        // We only receive the external connection - we don't own it, don't close it
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, tableId); // Set which table to mark as occupied

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new SQLException("Table with ID " + tableId + " not found when trying to mark as OCCUPIED.");
            }

            System.out.println("[TableDAO] Table " + tableId + " is now OCCUPIED (occupied_since set to NOW).");
        }
    }
}
