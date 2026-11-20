package com.pixelforge.restaurant.exception;

/**
 * ============================================================
 * ItemNotFoundException - Custom Checked Exception
 * ============================================================
 *
 * PURPOSE:
 * --------
 * Thrown when a menu item is requested by ID but does not exist
 * in the database (menu_items table). This provides a clear,
 * meaningful signal to the calling code rather than returning null
 * or throwing a generic exception.
 *
 * WHEN IS THIS THROWN?
 * --------------------
 * 1. MenuDAO.getMenuItemById(int itemId)
 *    → If no row matches the given itemId in the menu_items table,
 *      this exception is thrown with a message like:
 *      "Menu item with ID 42 does not exist in the database."
 *
 * 2. TableAndOrderService.addItemToOrder(int tableId, int itemId, int qty)
 *    → If the user enters an itemId that doesn't exist while placing an order.
 *
 * WHY CHECKED (extends Exception)?
 * ---------------------------------
 * - The caller (MainApp, service layer) MUST handle this explicitly.
 * - Prevents silent failures where an item is simply "not found" and
 *   the application continues with null data, causing NullPointerExceptions later.
 * - Forces the developer to show a helpful message to the user:
 *     "Error: Menu item with ID 99 not found. Please check the menu."
 *
 * CHECKED vs UNCHECKED:
 * ----------------------
 *   extends Exception        → Checked   → compiler enforces handling
 *   extends RuntimeException → Unchecked → optional handling (risky!)
 * ============================================================
 */
public class ItemNotFoundException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor with a descriptive message.
     *
     * @param message Description of which item was not found and why.
     *                e.g., "Menu item with ID 7 not found in the database."
     */
    public ItemNotFoundException(String message) {
        super(message); // Delegates to Exception(String message)
    }

    /**
     * Constructor with message and underlying cause.
     * Used for exception chaining when wrapping a lower-level error
     * (e.g., SQLException during DB lookup) with a business-level message.
     *
     * @param message High-level description of the error
     * @param cause   The original exception (e.g., SQLException)
     *
     * Example:
     *   } catch (SQLException e) {
     *       throw new ItemNotFoundException("DB error while fetching item ID: " + itemId, e);
     *   }
     */
    public ItemNotFoundException(String message, Throwable cause) {
        super(message, cause); // Both message and cause available via getMessage() and getCause()
    }
}
