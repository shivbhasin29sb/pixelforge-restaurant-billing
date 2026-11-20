package com.pixelforge.restaurant.exception;

/**
 * ============================================================
 * TableNotAvailableException - Custom Checked Exception
 * ============================================================
 *
 * WHY CUSTOM EXCEPTIONS?
 * ----------------------
 * Custom exceptions make error messages meaningful and context-specific,
 * compared to throwing a generic RuntimeException or IllegalStateException.
 *
 * Example:
 *   GENERIC:  throw new RuntimeException("Error");         // unhelpful
 *   CUSTOM:   throw new TableNotAvailableException(        // descriptive!
 *                 "Table 3 is currently occupied by customer 'Rahul'");
 *
 * HOW: extends Exception → it becomes a CHECKED exception, meaning:
 *   - The caller MUST either catch it (try-catch) or declare it (throws)
 *   - This forces the developer to handle the error explicitly
 *   - Unchecked (RuntimeException) can be ignored - bad for critical business logic
 *
 * USAGE IN THIS SYSTEM:
 *   - Thrown by TableAndOrderService.assignTable() when a table is occupied
 *   - Caught in MainApp.showTableManagement() to offer a waiting-list option
 *
 * KEYWORDS DEMONSTRATED:
 *   - throws  : declared in method signature (TableAndOrderService.assignTable() throws TableNotAvailableException)
 *   - throw   : used inside method body (throw new TableNotAvailableException(...))
 *   - catch   : used in MainApp to handle the exception gracefully
 * ============================================================
 */
public class TableNotAvailableException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor with a descriptive message.
     *
     * @param message Human-readable explanation of why the table is unavailable.
     *                e.g., "Table 3 is currently OCCUPIED. Please choose another table."
     */
    public TableNotAvailableException(String message) {
        super(message); // Passes message up to Exception → Throwable, stored internally
    }

    /**
     * Constructor with a message AND an underlying cause.
     * Useful for exception chaining — wrapping a lower-level exception
     * with a more meaningful higher-level one.
     *
     * @param message Human-readable explanation
     * @param cause   The original exception that caused this one
     *
     * Example:
     *   try {
     *       // DB lookup for table status
     *   } catch (SQLException e) {
     *       throw new TableNotAvailableException("Could not verify table status", e);
     *   }
     */
    public TableNotAvailableException(String message, Throwable cause) {
        super(message, cause); // Both message and cause propagated to Exception chain
    }
}
