package com.pixelforge.restaurant.dao;

// PixelForge Restaurant Management
/*
 * =============================================================================
 * BillDAO.java - Bill Persistence with ATOMIC JDBC Transactions
 * =============================================================================
 *
 * ============================================================
 * UNDERSTANDING ACID TRANSACTIONS - WHY THIS MATTERS
 * ============================================================
 *
 * When a customer pays and leaves, two things MUST happen together:
 *   1. Save the bill record in the 'bills' table
 *   2. Mark the restaurant table as 'AVAILABLE' again
 *
 * What if the app crashes between step 1 and step 2?
 *   - Bill is saved → customer is charged ✓
 *   - Table still shows OCCUPIED → new customers can't be seated ✗
 *   - Revenue is LOST because the table can never be used again!
 *
 * This is exactly the kind of data corruption that TRANSACTIONS prevent.
 *
 * ACID stands for:
 * ----------------
 * A - ATOMICITY:   Either ALL operations succeed, or NONE of them do.
 *                  "All or nothing" - no partial updates.
 *
 * C - CONSISTENCY: The database moves from one valid state to another valid state.
 *                  Business rules are always maintained.
 *
 * I - ISOLATION:   Concurrent transactions don't interfere with each other.
 *                  Other users won't see half-completed operations.
 *
 * D - DURABILITY:  Once committed, the transaction is permanent - even if
 *                  the system crashes immediately after.
 *
 * HOW WE IMPLEMENT TRANSACTIONS IN JDBC:
 * ----------------------------------------
 *   conn.setAutoCommit(false)  → Start transaction (disable auto-save)
 *   // ... execute SQL operations ...
 *   conn.commit()              → Permanently save ALL changes
 *   conn.rollback()            → Undo ALL changes if something went wrong
 *   conn.setAutoCommit(true)   → Restore normal behavior (each SQL auto-saved)
 *
 * Without setAutoCommit(false), every SQL statement is immediately committed.
 * With it, we can group multiple SQL statements into ONE atomic unit.
 *
 * =============================================================================
 */

import com.pixelforge.restaurant.model.Bill; // Model class representing a restaurant bill

import java.sql.Connection;          // Database connection
import java.sql.PreparedStatement;   // Parameterized SQL query (safe, fast)
import java.sql.ResultSet;           // Results from SELECT queries
import java.sql.SQLException;        // Database errors
import java.sql.Statement;           // For RETURN_GENERATED_KEYS constant
import java.sql.Timestamp;           // For storing/reading date-time values
import java.time.LocalDateTime;      // Java 8+ date-time (without timezone)
import java.util.ArrayList;          // Dynamic array for collecting results
import java.util.List;               // List interface

/**
 * BillDAO - Handles all database operations for the 'bills' table.
 *
 * Key feature: saveBillAndFreeTable() uses a JDBC transaction to ensure
 * that saving a bill and freeing the table are ATOMIC operations.
 *
 * Table schema:
 *   bill_id      INT AUTO_INCREMENT PRIMARY KEY
 *   table_id     INT (FK → restaurant_tables.table_id)
 *   bill_details TEXT (formatted string of ordered items)
 *   subtotal     DECIMAL(10,2)
 *   gst          DECIMAL(10,2)
 *   grand_total  DECIMAL(10,2)
 *   bill_time    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
 */
public class BillDAO {

    // =========================================================================
    // THE KEY METHOD: Save bill + Free table (ATOMIC TRANSACTION)
    // =========================================================================

    /**
     * THE CORE BILLING OPERATION - saves the bill and marks the table as free
     * in a single ATOMIC database transaction.
     *
     * Step-by-step what happens:
     *   1. Get a database connection
     *   2. Turn off auto-commit (begin transaction)
     *   3. INSERT the bill into the 'bills' table
     *   4. UPDATE the table's status to 'AVAILABLE' (clears occupied_since)
     *   5. COMMIT → permanently save both changes
     *   6. If ANYTHING throws an exception → ROLLBACK → undo both changes
     *   7. Always restore auto-commit in finally block
     *
     * @param bill    The Bill object containing all billing details
     * @param tableId The restaurant table number being freed after payment
     * @throws SQLException if the transaction fails (rollback is automatic)
     */
    public void saveBillAndFreeTable(Bill bill, int tableId) throws SQLException {
        // Get the shared connection - we need this for the full transaction
        Connection conn = DBConnection.getConnection();

        // -------------------------------------------------------------------------
        // STEP 2: Disable auto-commit to begin the manual transaction
        // -------------------------------------------------------------------------
        // By default, every SQL statement is immediately committed (auto-commit=true).
        // Setting it to false means: "Don't save anything until I say conn.commit()"
        conn.setAutoCommit(false);
        System.out.println("[BillDAO] Transaction started (auto-commit disabled).");

        try {
            // -----------------------------------------------------------------
            // STEP 3: INSERT the bill into the bills table
            // -----------------------------------------------------------------
            String insertBillSql =
                "INSERT INTO bills (table_id, bill_details, subtotal, gst, grand_total, bill_time) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

            // RETURN_GENERATED_KEYS: after INSERT, get the auto-generated bill_id back
            try (PreparedStatement billStmt = conn.prepareStatement(insertBillSql, Statement.RETURN_GENERATED_KEYS)) {

                // Bind all the bill fields to the ? placeholders
                billStmt.setInt(1, tableId);                                           // Which table this bill is for
                billStmt.setString(2, bill.getBillDetails());                          // Formatted order details text
                billStmt.setDouble(3, bill.getSubtotal());                             // Pre-tax total
                billStmt.setDouble(4, bill.getGst());                                  // 5% GST amount
                billStmt.setDouble(5, bill.getGrandTotal());                           // subtotal + gst
                LocalDateTime bTime = bill.getBillTime() != null ? bill.getBillTime() : LocalDateTime.now();
                billStmt.setTimestamp(6, Timestamp.valueOf(bTime));                    // When the bill was generated

                // Execute the INSERT
                int rowsInserted = billStmt.executeUpdate();
                if (rowsInserted == 0) {
                    // If nothing was inserted, something is wrong - force a rollback
                    throw new SQLException("Bill INSERT failed - no rows were affected.");
                }

                // Retrieve the auto-generated bill_id from MySQL
                try (ResultSet generatedKeys = billStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        int generatedBillId = generatedKeys.getInt(1);
                        bill.setBillId(generatedBillId); // Update the Bill object with its DB ID
                        System.out.println("[BillDAO] Bill inserted with ID: " + generatedBillId);
                    }
                }
            }

            // -----------------------------------------------------------------
            // STEP 4: UPDATE the restaurant table's status to AVAILABLE
            // -----------------------------------------------------------------
            // This runs on the SAME connection/transaction as the bill INSERT above.
            // If this fails, we'll rollback the bill INSERT too (atomicity!).
            String updateTableSql =
                "UPDATE restaurant_tables SET status = 'AVAILABLE', occupied_since = NULL WHERE table_id = ?";

            try (PreparedStatement tableStmt = conn.prepareStatement(updateTableSql)) {
                tableStmt.setInt(1, tableId); // Which table to free up

                int rowsUpdated = tableStmt.executeUpdate();
                if (rowsUpdated == 0) {
                    throw new SQLException("Table UPDATE failed - table ID " + tableId + " not found.");
                }

                System.out.println("[BillDAO] Table " + tableId + " marked as AVAILABLE.");
            }

            // -----------------------------------------------------------------
            // STEP 5: COMMIT - permanently save BOTH operations
            // -----------------------------------------------------------------
            // Only reached if BOTH the INSERT and UPDATE succeeded!
            conn.commit();
            System.out.println("[BillDAO] Transaction COMMITTED successfully. Bill saved, table freed.");

        } catch (SQLException e) {
            // -----------------------------------------------------------------
            // STEP 6: ROLLBACK - undo everything if any step failed
            // -----------------------------------------------------------------
            // We get here if either the INSERT or the UPDATE threw an exception.
            // rollback() undoes ALL changes made since setAutoCommit(false).
            // This means: if the bill was inserted but the table update failed,
            // the bill insert is ALSO undone. The database stays consistent.
            System.err.println("[BillDAO] Transaction FAILED! Rolling back all changes... Error: " + e.getMessage());
            try {
                conn.rollback();
                System.out.println("[BillDAO] Rollback successful. No changes were saved.");
            } catch (SQLException rollbackEx) {
                System.err.println("[BillDAO] Critical: Rollback itself failed! " + rollbackEx.getMessage());
            }
            // Re-throw the original exception so the caller knows it failed
            throw e;

        } finally {
            // -----------------------------------------------------------------
            // STEP 7: ALWAYS restore auto-commit to true
            // -----------------------------------------------------------------
            // The 'finally' block runs no matter what (success or exception).
            // We MUST restore auto-commit, or future SQL queries using this
            // connection will also be part of an uncommitted transaction!
            try {
                conn.setAutoCommit(true);
                System.out.println("[BillDAO] Auto-commit restored to true.");
            } catch (SQLException autoCommitEx) {
                System.err.println("[BillDAO] Warning: Could not restore auto-commit: " + autoCommitEx.getMessage());
            }
        }
    }

    // =========================================================================
    // READ: Get all bills (for analytics and daily reports)
    // =========================================================================

    /**
     * Retrieves all bills ever generated, ordered by most recent first.
     *
     * Used by BillingAndAnalyticsService to calculate:
     *   - Daily revenue totals
     *   - Number of bills generated
     *   - Any time-based analytics
     *
     * SQL:  SELECT * FROM bills ORDER BY bill_time DESC
     *
     * @return List of all Bill objects from the database (may be empty)
     * @throws SQLException if the query fails
     */
    public List<Bill> getAllBills() throws SQLException {
        List<Bill> bills = new ArrayList<>();

        // Select all columns, most recent bills first
        String sql = "SELECT bill_id, table_id, bill_details, subtotal, gst, grand_total, bill_time " +
                     "FROM bills ORDER BY bill_time DESC";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                // Read the bill_time TIMESTAMP column and convert to LocalDateTime
                // getTimestamp() returns a java.sql.Timestamp
                // .toLocalDateTime() converts it to the modern java.time.LocalDateTime
                Timestamp billTimeStamp = rs.getTimestamp("bill_time");
                LocalDateTime billTime = (billTimeStamp != null) ? billTimeStamp.toLocalDateTime() : LocalDateTime.now();

                // Construct a Bill object from each row
                Bill bill = new Bill(
                        rs.getInt("bill_id"),
                        rs.getInt("table_id"),
                        rs.getString("bill_details"),
                        rs.getDouble("subtotal"),
                        rs.getDouble("gst"),
                        rs.getDouble("grand_total"),
                        billTime
                );
                bills.add(bill);
            }
        }

        System.out.println("[BillDAO] Retrieved " + bills.size() + " bill(s) from database.");
        return bills;
    }
}
