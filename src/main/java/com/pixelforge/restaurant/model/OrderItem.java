package com.pixelforge.restaurant.model;

/**
 * ============================================================
 * MODEL CLASS: OrderItem
 * ============================================================
 * An OrderItem represents ONE line in a customer's order.
 * It links a specific MenuItem with the quantity the customer
 * has ordered.
 *
 * Example: A customer orders 2 plates of Paneer Tikka.
 *   menuItem  = MenuItem { id=1, name="Paneer Tikka", price=220.0 }
 *   quantity  = 2
 *   itemTotal = 220.0 * 2 = ₹440.0
 *
 * COMPOSITION (OOP concept):
 * OrderItem "has a" MenuItem. This is called composition —
 * one class containing a reference to another class as a field.
 * ============================================================
 */
public class OrderItem {

    // The actual menu item being ordered (composition relationship)
    private MenuItem menuItem;

    // How many units of this item were ordered (must be >= 1)
    private int quantity;

    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    /**
     * Creates an OrderItem with a specific menu item and quantity.
     *
     * @param menuItem The MenuItem being ordered (must not be null)
     * @param quantity How many units are ordered (must be >= 1)
     */
    public OrderItem(MenuItem menuItem, int quantity) {
        if (menuItem == null) {
            throw new IllegalArgumentException("MenuItem cannot be null.");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1. Got: " + quantity);
        }
        this.menuItem = menuItem;
        this.quantity = quantity;
    }

    // ============================================================
    // GETTERS
    // ============================================================

    /** Returns the MenuItem associated with this order line. */
    public MenuItem getMenuItem() {
        return menuItem;
    }

    /** Returns the quantity ordered. */
    public int getQuantity() {
        return quantity;
    }

    // ============================================================
    // SETTERS
    // ============================================================

    /** Updates the MenuItem for this order line. */
    public void setMenuItem(MenuItem menuItem) {
        this.menuItem = menuItem;
    }

    /** Updates the quantity. Must be greater than 0. */
    public void setQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1. Got: " + quantity);
        }
        this.quantity = quantity;
    }

    // ============================================================
    // BUSINESS METHOD: getItemTotal()
    // This is a "derived" value — calculated from existing fields.
    // We don't store it as a field because it can always be
    // recomputed from price × quantity.
    // ============================================================

    /**
     * Calculates and returns the total cost for this order line.
     * Formula: price per unit × quantity ordered
     *
     * @return Total price for this order line in ₹
     */
    public double getItemTotal() {
        // menuItem.getPrice() uses the GETTER — respecting encapsulation.
        return menuItem.getPrice() * quantity;
    }

    // ============================================================
    // toString()
    // ============================================================

    /**
     * Returns a formatted line suitable for printing on a bill receipt.
     * Example: "  - Paneer Tikka x 2 = ₹440.0"
     * The leading spaces create an indented look on the receipt.
     */
    @Override
    public String toString() {
        return "  - " + menuItem.getName()
             + " x " + quantity
             + " = ₹" + getItemTotal();
    }
}
