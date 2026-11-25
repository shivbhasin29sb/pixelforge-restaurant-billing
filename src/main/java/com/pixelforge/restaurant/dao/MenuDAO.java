package com.pixelforge.restaurant.dao;

/*
 * =============================================================================
 * MenuDAO.java - Data Access Object for menu_items table
 * =============================================================================
 *
 * WHAT IS A DAO (Data Access Object)?
 * ------------------------------------
 * A DAO is a design pattern that separates the DATABASE LOGIC from the
 * BUSINESS LOGIC. Instead of writing SQL queries scattered throughout your
 * code, you put all SQL for a particular table in one DAO class.
 *
 * Benefits:
 *   - Easy to change database (just rewrite the DAO, not the whole app)
 *   - Code is organized and easy to test
 *   - SQL errors are caught in one place
 *
 * WHAT IS PreparedStatement?
 * --------------------------
 * PreparedStatement is SAFER and FASTER than raw Statement:
 *   - SAFE: Prevents SQL Injection attacks (a major security vulnerability)
 *     e.g., a user typing  ' OR '1'='1  can't hack the query
 *   - FAST: The DB pre-compiles the query template once, then reuses it
 *   - CLEAN: Uses ? placeholders instead of messy string concatenation
 *
 * WHAT IS try-with-resources?
 * ---------------------------
 * The syntax:  try (Resource r = new Resource()) { ... }
 * Automatically calls r.close() when the try block ends - even if an
 * exception is thrown. This prevents resource leaks (open DB cursors, etc.)
 *
 * =============================================================================
 */

import com.pixelforge.restaurant.exception.ItemNotFoundException; // Custom exception for missing menu items
import com.pixelforge.restaurant.model.MenuItem;                 // Model class for a menu item

import java.sql.Connection;         // Represents the database connection
import java.sql.PreparedStatement;  // Safe, parameterized SQL statement
import java.sql.ResultSet;          // Table of results returned by a SELECT query
import java.sql.SQLException;       // Thrown when any DB operation fails
import java.sql.Statement;          // Needed for RETURN_GENERATED_KEYS
import java.util.ArrayList;         // Dynamic list to collect query results
import java.util.List;              // Interface type for the return value

/**
 * MenuDAO - Handles all Create, Read, Update, Delete (CRUD) operations
 * on the 'menu_items' table in the pixelforge_restaurant database.
 */
public class MenuDAO {

    // =========================================================================
    // READ: Get all menu items
    // =========================================================================

    /**
     * Fetches every row from the menu_items table and returns them as a List.
     *
     * SQL:  SELECT item_id, name, category, price FROM menu_items ORDER BY category, name
     *
     * @return List of MenuItem objects (empty list if table has no rows)
     * @throws SQLException if the database query fails
     */
    public List<MenuItem> getAllMenuItems() throws SQLException {
        // This list will hold all the menu items we find
        List<MenuItem> menuItems = new ArrayList<>();

        // The SQL query - note we use ORDER BY so the UI gets nicely sorted results
        String sql = "SELECT item_id, name, category, price FROM menu_items ORDER BY category, name";

        // try-with-resources: automatically closes Connection and PreparedStatement
        // when this block exits (success OR exception)
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {   // executeQuery() runs SELECT statements

            // Loop through every row returned by the query
            // rs.next() moves the cursor to the next row; returns false when done
            while (rs.next()) {
                // Create a MenuItem object from the current row's columns
                // getInt("column_name") reads an integer column
                // getString("column_name") reads a text column
                // getDouble("column_name") reads a decimal/floating-point column
                MenuItem item = new MenuItem(
                        rs.getInt("item_id"),        // Primary key
                        rs.getString("name"),         // Item name (e.g., "Butter Chicken")
                        rs.getString("category"),     // Category (e.g., "Main Course")
                        rs.getDouble("price")         // Price in INR (e.g., 320.00)
                );
                menuItems.add(item); // Add to our collection
            }
        }
        // The connection and statement are automatically closed here by try-with-resources

        return menuItems; // Return the populated list (may be empty if table is empty)
    }

    // =========================================================================
    // READ: Get a single menu item by its ID
    // =========================================================================

    /**
     * Looks up a specific menu item by its primary key (item_id).
     *
     * SQL:  SELECT item_id, name, category, price FROM menu_items WHERE item_id = ?
     *
     * The '?' is a PLACEHOLDER. We set its value safely using pstmt.setInt(1, itemId).
     * This prevents SQL injection - no matter what 'itemId' is, it's treated as data,
     * never as SQL code.
     *
     * @param itemId The primary key of the menu item to fetch
     * @return The matching MenuItem object
     * @throws ItemNotFoundException if no menu item exists with that ID
     * @throws SQLException if the database query fails
     */
    public MenuItem getMenuItemById(int itemId) throws ItemNotFoundException, SQLException {
        // SQL with a ? placeholder for the item ID
        String sql = "SELECT item_id, name, category, price FROM menu_items WHERE item_id = ?";

        // try-with-resources for automatic cleanup
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Set the first ? placeholder (index 1) to our itemId value
            // setInt(parameterIndex, value) - parameterIndex starts at 1 (not 0!)
            pstmt.setInt(1, itemId);

            // Execute the query and get results
            // We use a nested try-with-resources for the ResultSet
            try (ResultSet rs = pstmt.executeQuery()) {

                // rs.next() returns true if at least one row was found
                if (rs.next()) {
                    // Row found - build and return the MenuItem
                    return new MenuItem(
                            rs.getInt("item_id"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getDouble("price")
                    );
                } else {
                    // No row matched the given itemId
                    // Throw our custom exception with a helpful message
                    throw new ItemNotFoundException(
                            "Menu item with ID " + itemId + " was not found in the database."
                    );
                }
            }
        }
    }

    // =========================================================================
    // CREATE: Add a new menu item
    // =========================================================================

    /**
     * Inserts a new row into the menu_items table.
     *
     * SQL:  INSERT INTO menu_items (name, category, price) VALUES (?, ?, ?)
     *
     * Note: item_id is AUTO_INCREMENT so we don't insert it - MySQL assigns it.
     * We use Statement.RETURN_GENERATED_KEYS to retrieve the newly assigned ID.
     *
     * @param item The MenuItem object containing the data to insert
     * @throws SQLException if the insert fails (e.g., duplicate name, connection lost)
     */
    public void addMenuItem(MenuItem item) throws SQLException {
        // INSERT statement with 3 placeholders for name, category, price
        // We omit item_id because MySQL auto-generates it (AUTO_INCREMENT)
        String sql = "INSERT INTO menu_items (name, category, price) VALUES (?, ?, ?)";

        // RETURN_GENERATED_KEYS tells MySQL: after inserting, give us back the
        // auto-generated primary key so we can update our Java object with it
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Bind values to the ? placeholders in order:
            pstmt.setString(1, item.getName());      // 1st ? = item name
            pstmt.setString(2, item.getCategory());  // 2nd ? = category
            pstmt.setDouble(3, item.getPrice());     // 3rd ? = price

            // executeUpdate() runs INSERT/UPDATE/DELETE statements
            // Returns the number of rows affected (should be 1 here)
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                throw new SQLException("Failed to insert menu item - no rows were affected.");
            }

            // Retrieve the auto-generated item_id from the database
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    // Update the MenuItem object with the new DB-assigned ID
                    item.setItemId(generatedKeys.getInt(1));
                    System.out.println("[MenuDAO] Added menu item with ID: " + item.getItemId());
                }
            }
        }
    }

    // =========================================================================
    // UPDATE: Modify an existing menu item
    // =========================================================================

    /**
     * Updates an existing menu item's details in the database.
     *
     * SQL:  UPDATE menu_items SET name=?, category=?, price=? WHERE item_id=?
     *
     * The WHERE clause with item_id ensures we only update ONE specific row.
     * Without WHERE, we'd accidentally update EVERY row in the table!
     *
     * @param item MenuItem with updated values (item_id must already be set)
     * @throws SQLException if the update fails
     */
    public void updateMenuItem(MenuItem item) throws ItemNotFoundException, SQLException {
        // UPDATE with SET for each column, and WHERE to target the specific row
        String sql = "UPDATE menu_items SET name = ?, category = ?, price = ? WHERE item_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Bind the new values to the placeholders:
            pstmt.setString(1, item.getName());      // 1st ? = new name
            pstmt.setString(2, item.getCategory());  // 2nd ? = new category
            pstmt.setDouble(3, item.getPrice());     // 3rd ? = new price
            pstmt.setInt(4, item.getItemId());        // 4th ? = which row to update (WHERE clause)

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                // No rows were updated - the item_id probably doesn't exist
                throw new ItemNotFoundException(
                        "Cannot update: menu item with ID " + item.getItemId() + " not found."
                );
            }

            System.out.println("[MenuDAO] Updated menu item ID: " + item.getItemId());
        }
    }

    // =========================================================================
    // DELETE: Remove a menu item
    // =========================================================================

    /**
     * Deletes a menu item from the database by its ID.
     *
     * SQL:  DELETE FROM menu_items WHERE item_id = ?
     *
     * WARNING: This is a permanent delete! In a real app, consider using
     * a 'soft delete' (setting an is_active = false flag) instead.
     *
     * @param itemId The primary key of the menu item to delete
     * @throws SQLException if the delete fails
     */
    public void deleteMenuItem(int itemId) throws ItemNotFoundException, SQLException {
        // DELETE with WHERE to target only the specific item
        String sql = "DELETE FROM menu_items WHERE item_id = ?";

        try (Connection conn = DBConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Set the item_id we want to delete
            pstmt.setInt(1, itemId);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected == 0) {
                // The item wasn't found - throw our custom exception
                throw new ItemNotFoundException(
                        "Cannot delete: menu item with ID " + itemId + " not found."
                );
            }

            System.out.println("[MenuDAO] Deleted menu item ID: " + itemId);
        }
    }
}
