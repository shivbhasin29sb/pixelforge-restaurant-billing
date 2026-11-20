package com.pixelforge.restaurant.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ============================================================
 * MODEL CLASS: RestaurantTable
 * ============================================================
 * Represents a physical dining table in the restaurant.
 * A table can be in one of two states:
 *   - "AVAILABLE" : No customer is seated; can be assigned.
 *   - "OCCUPIED"  : A customer is currently seated.
 *
 * The 'occupiedSince' field tracks WHEN a table was occupied.
 * It is nullable (can be null) — meaning if the table is
 * AVAILABLE, there is no meaningful "occupied since" time,
 * so we store null.
 *
 * LocalDateTime: Part of the java.time API (Java 8+).
 * It holds both date AND time (e.g., 2024-12-25T19:30:00).
 * ============================================================
 */
public class RestaurantTable {

    private int tableId;                   // Unique table number (e.g., 1, 2, 3...)
    private int capacity;                  // How many people can sit at this table
    private String status;                 // "AVAILABLE" or "OCCUPIED"
    private LocalDateTime occupiedSince;   // When the table was occupied (null if AVAILABLE)

    // ============================================================
    // CONSTRUCTORS
    // ============================================================

    /**
     * No-arg constructor — creates an empty RestaurantTable object.
     * Useful when loading data from the database row by row.
     */
    public RestaurantTable() {
        // Java sets defaults: int -> 0, String -> null, Object -> null
    }

    /**
     * Full constructor — initialises all fields.
     *
     * @param tableId       Unique identifier for the table
     * @param capacity      Number of seats at this table
     * @param status        "AVAILABLE" or "OCCUPIED"
     * @param occupiedSince The time the table was occupied; null if available
     */
    public RestaurantTable(int tableId, int capacity, String status, LocalDateTime occupiedSince) {
        this.tableId       = tableId;
        this.capacity      = capacity;
        this.status        = status;
        this.occupiedSince = occupiedSince;
    }

    // ============================================================
    // GETTERS
    // ============================================================

    /** Returns the table's unique ID. */
    public int getTableId() {
        return tableId;
    }

    /** Returns the seating capacity of this table. */
    public int getCapacity() {
        return capacity;
    }

    /** Returns the current status: "AVAILABLE" or "OCCUPIED". */
    public String getStatus() {
        return status;
    }

    /**
     * Returns the time this table was last occupied.
     * May return null if the table is currently AVAILABLE.
     */
    public LocalDateTime getOccupiedSince() {
        return occupiedSince;
    }

    // ============================================================
    // SETTERS
    // ============================================================

    /** Sets the table's unique ID. */
    public void setTableId(int tableId) {
        this.tableId = tableId;
    }

    /** Sets the seating capacity. */
    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    /**
     * Sets the status of this table.
     * Only "AVAILABLE" or "OCCUPIED" are valid values.
     */
    public void setStatus(String status) {
        if (!"AVAILABLE".equals(status) && !"OCCUPIED".equals(status)) {
            throw new IllegalArgumentException(
                "Invalid table status: '" + status + "'. Must be 'AVAILABLE' or 'OCCUPIED'."
            );
        }
        this.status = status;
    }

    /**
     * Sets when this table was occupied.
     * Pass null when freeing the table (setting it back to AVAILABLE).
     */
    public void setOccupiedSince(LocalDateTime occupiedSince) {
        this.occupiedSince = occupiedSince;
    }

    // ============================================================
    // toString()
    // ============================================================

    /**
     * Returns a human-readable summary of this table.
     * Example outputs:
     *   "Table #2 | Capacity: 4 | Status: AVAILABLE"
     *   "Table #3 | Capacity: 6 | Status: OCCUPIED since 20-Nov-2024 07:15 PM"
     */
    @Override
    public String toString() {
        // DateTimeFormatter lets us convert LocalDateTime to a readable String.
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh:mm a");

        String sinceInfo = (occupiedSince != null)
                ? " since " + occupiedSince.format(formatter)
                : "";

        return "Table #" + tableId
             + " | Capacity: " + capacity
             + " | Status: " + status + sinceInfo;
    }
}
