package com.pixelforge.restaurant.model;

import com.pixelforge.restaurant.exception.InvalidQuantityException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * MODEL CLASS: Order
 * ============================================================
 * Represents a customer's active order at a specific table.
 * An Order is essentially a LIST of OrderItems accumulated
 * during a dining session.
 *
 * Key Design Decisions:
 *   - We use ArrayList<OrderItem> because:
 *       • We need ordered, indexed access.
 *       • Items are added/removed frequently.
 *       • We need to iterate over all items to compute subtotal.
 *   - The order is associated with a tableId (not a Table object)
 *     to keep it lightweight and avoid tight coupling.
 *
 * EXCEPTION HANDLING:
 *   updateQuantity() throws InvalidQuantityException (a custom
 *   checked exception) when an invalid quantity is provided.
 *   This forces callers to handle bad input explicitly.
 * ============================================================
 */
public class Order {

    private int tableId;                   // Which table placed this order
    private List<OrderItem> items;         // The list of ordered items
    private LocalDateTime orderTime;       // When the order was first created

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    /**
     * Creates a new Order for the given table.
     * Initialises an empty item list and records the current time.
     *
     * @param tableId The ID of the table placing this order
     */
    public Order(int tableId) {
        this.tableId   = tableId;
        this.items     = new ArrayList<>();  // Start with an empty order list
        this.orderTime = LocalDateTime.now(); // Record when the order was created
    }

    // ============================================================
    // GETTERS
    // ============================================================

    /** Returns the ID of the table that placed this order. */
    public int getTableId() {
        return tableId;
    }

    /**
     * Returns the list of OrderItems in this order.
     * Note: returning the list directly allows callers to iterate
     * and inspect items but we control modifications via addItem/removeItem.
     */
    public List<OrderItem> getItems() {
        return items;
    }

    /** Returns the time this order was created. */
    public LocalDateTime getOrderTime() {
        return orderTime;
    }

    // ============================================================
    // ORDER MANAGEMENT METHODS
    // ============================================================

    /**
     * Adds a new OrderItem to this order.
     * If the same menu item already exists, it increases its quantity
     * instead of adding a duplicate line.
     *
     * @param item The OrderItem to add (must not be null)
     */
    public void addItem(OrderItem item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot add a null OrderItem.");
        }

        // Check if this menu item is already in the order
        for (OrderItem existing : items) {
            if (existing.getMenuItem().getItemId() == item.getMenuItem().getItemId()) {
                // Item already exists — just increase the quantity
                existing.setQuantity(existing.getQuantity() + item.getQuantity());
                return; // Done — no need to add a new line
            }
        }

        // If we reach here, it's a brand-new item — add it to the list
        items.add(item);
    }

    /**
     * Removes an item from the order by its menu item ID.
     * If the item is not found, the order remains unchanged (no error thrown).
     *
     * @param menuItemId The ID of the MenuItem to remove from this order
     */
    public void removeItem(int menuItemId) {
        // removeIf() is a List method that removes all elements matching a condition.
        // The condition (lambda): remove the OrderItem if its menuItem's ID matches.
        items.removeIf(item -> item.getMenuItem().getItemId() == menuItemId);
    }

    /**
     * Updates the quantity of a specific item in the order.
     *
     * @param menuItemId The ID of the MenuItem whose quantity to update
     * @param newQty     The new quantity (must be > 0)
     * @throws InvalidQuantityException if newQty is 0 or negative
     */
    public void updateQuantity(int menuItemId, int newQty) throws InvalidQuantityException {
        // VALIDATE: Quantity must be at least 1.
        // We throw our custom exception so the caller is FORCED to handle this case.
        if (newQty <= 0) {
            throw new InvalidQuantityException(
                "Quantity must be greater than 0. Received: " + newQty
            );
        }

        // Find the matching order item and update its quantity
        for (OrderItem item : items) {
            if (item.getMenuItem().getItemId() == menuItemId) {
                item.setQuantity(newQty);
                return; // Update done
            }
        }

        // If we reach here, the item wasn't found — just ignore silently
        System.out.println("[Order] Warning: Item ID " + menuItemId + " not found in order for table " + tableId);
    }

    /**
     * Calculates and returns the subtotal for this order.
     * This is the sum of getItemTotal() across ALL OrderItems.
     *
     * We use a simple for-each loop here. The BillingService uses
     * Stream API to do the same thing more elegantly.
     *
     * @return Total cost before GST in ₹
     */
    public double getSubtotal() {
        double total = 0.0;
        for (OrderItem item : items) {
            total += item.getItemTotal(); // Each OrderItem knows its own total
        }
        return total;
    }

    // ============================================================
    // toString()
    // ============================================================

    /**
     * Returns a formatted summary of the entire order.
     * Lists all items and the running subtotal.
     *
     * Example output:
     *   === Order for Table #3 (placed at 07:30 PM) ===
     *     - Paneer Tikka x 2 = ₹440.0
     *     - Butter Naan x 3 = ₹120.0
     *   Subtotal: ₹560.0
     */
    @Override
    public String toString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
        StringBuilder sb = new StringBuilder();

        sb.append("=== Order for Table #").append(tableId)
          .append(" (placed at ").append(orderTime.format(formatter)).append(") ===\n");

        if (items.isEmpty()) {
            sb.append("  (No items ordered yet)\n");
        } else {
            for (OrderItem item : items) {
                // Each OrderItem's toString() gives us "  - name x qty = ₹total"
                sb.append(item.toString()).append("\n");
            }
        }

        sb.append("  Subtotal: ₹").append(String.format("%.2f", getSubtotal()));
        return sb.toString();
    }
}
