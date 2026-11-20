package com.pixelforge.restaurant.exception;

/**
 * ============================================================
 * InvalidQuantityException - Custom Checked Exception
 * ============================================================
 *
 * PURPOSE:
 * --------
 * Thrown when an order operation is attempted with an invalid quantity.
 * "Invalid" means: quantity <= 0 (zero or negative), which makes no
 * business sense for a restaurant ordering system.
 *
 * WHEN IS THIS THROWN?
 * --------------------
 * 1. TableAndOrderService.addItemToOrder(int tableId, int itemId, int qty)
 *    → If qty <= 0:
 *      throw new InvalidQuantityException(
 *          "Quantity must be at least 1. You entered: " + qty);
 *
 * 2. TableAndOrderService.updateItemQuantity(int tableId, int itemId, int newQty)
 *    → If newQty <= 0 (to remove an item, use removeItemFromOrder instead):
 *      throw new InvalidQuantityException(
 *          "Updated quantity must be positive. Use Remove Item to delete an item.");
 *
 * WHY THIS MATTERS:
 * -----------------
 * Without this validation:
 *   - A user could order -2 Butter Chickens (negative quantity)
 *   - Subtotal calculation would go negative → incorrect bill
 *   - Database would store nonsensical data
 *
 * With this exception:
 *   - The error is caught at the SERVICE layer before any DB write
 *   - The UI shows a clear, user-friendly message
 *   - Data integrity is maintained
 *
 * BUSINESS RULE ENFORCED:
 *   quantity > 0  → Valid order
 *   quantity <= 0 → Throw InvalidQuantityException
 * ============================================================
 */
public class InvalidQuantityException extends Exception {

    private static final long serialVersionUID = 1L;

    /**
     * Constructor with a descriptive message.
     *
     * @param message Explanation of what quantity was invalid and why.
     *                e.g., "Quantity -3 is invalid. Must be greater than 0."
     */
    public InvalidQuantityException(String message) {
        super(message); // Passes to Exception(String message) → Throwable
    }

    /**
     * Constructor with message and underlying cause for exception chaining.
     * Useful when catching a NumberFormatException from input parsing and
     * re-throwing as a domain-level exception.
     *
     * @param message Business-level description of the validation failure
     * @param cause   Original exception (e.g., NumberFormatException from parseInt)
     *
     * Example:
     *   try {
     *       int qty = Integer.parseInt(input);
     *       if (qty <= 0) throw new InvalidQuantityException("Qty must be > 0");
     *   } catch (NumberFormatException e) {
     *       throw new InvalidQuantityException("Quantity must be a valid number", e);
     *   }
     */
    public InvalidQuantityException(String message, Throwable cause) {
        super(message, cause);
    }
}
