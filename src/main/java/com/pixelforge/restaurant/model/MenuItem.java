package com.pixelforge.restaurant.model;

/**
 * ============================================================
 * MODEL CLASS: MenuItem
 * ============================================================
 * What is a Model class?
 * A "model" class (also called a POJO - Plain Old Java Object)
 * represents a real-world entity. Here, MenuItem represents
 * a food/drink item on the restaurant menu.
 *
 * ENCAPSULATION (OOP Pillar #1):
 * All fields are marked 'private' — they cannot be accessed
 * directly from outside this class. Instead, we use:
 *   - Getters: methods to READ the value  (e.g., getName())
 *   - Setters: methods to WRITE the value (e.g., setName("Pasta"))
 * This gives us CONTROL over how data is read or changed.
 * ============================================================
 */
public class MenuItem {

    // 'private' means only THIS class can directly access these variables.
    // This is ENCAPSULATION — hiding the internal state.
    private int itemId;       // Unique ID for each menu item (matches DB primary key)
    private String name;      // Name of the dish, e.g., "Paneer Tikka"
    private String category;  // Category, e.g., "Starters", "Main Course", "Beverages"
    private double price;     // Price in Indian Rupees (₹)

    // ============================================================
    // CONSTRUCTORS
    // A constructor is a special method called when you create
    // a new object using the 'new' keyword.
    // ============================================================

    /**
     * No-argument constructor (no-arg constructor).
     * Required by many frameworks and for creating empty objects
     * before setting values one by one.
     * Example: MenuItem item = new MenuItem();
     */
    public MenuItem() {
        // body intentionally empty; Java sets defaults:
        // int -> 0, String -> null, double -> 0.0
    }

    /**
     * Full constructor — initialises all fields at once.
     * Example: MenuItem item = new MenuItem(1, "Paneer Tikka", "Starters", 220.0);
     *
     * @param itemId   The unique identifier for the menu item
     * @param name     The display name of the dish
     * @param category The menu category it belongs to
     * @param price    The selling price in ₹
     */
    public MenuItem(int itemId, String name, String category, double price) {
        // 'this.fieldName' refers to the INSTANCE variable (the field on the object)
        // 'fieldName' alone refers to the parameter passed into the constructor.
        this.itemId   = itemId;
        this.name     = name;
        this.category = category;
        this.price    = price;
    }

    // ============================================================
    // GETTERS — Public methods that let outsiders READ private fields
    // ============================================================

    /** Returns the item's unique ID. */
    public int getItemId() {
        return itemId;
    }

    /** Returns the name of the menu item. */
    public String getName() {
        return name;
    }

    /** Returns the category this item belongs to. */
    public String getCategory() {
        return category;
    }

    /** Returns the price of this item in ₹. */
    public double getPrice() {
        return price;
    }

    // ============================================================
    // SETTERS — Public methods that let outsiders UPDATE private fields
    // We can add validation logic here (e.g., price must be > 0).
    // ============================================================

    /** Sets the item's unique ID. */
    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    /** Sets the name of the menu item. */
    public void setName(String name) {
        this.name = name;
    }

    /** Sets the category of the menu item. */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * Sets the price. We validate that it is not negative —
     * this is one benefit of using setters instead of public fields.
     */
    public void setPrice(double price) {
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative: " + price);
        }
        this.price = price;
    }

    // ============================================================
    // toString()
    // Every Java class inherits a default toString() from Object.
    // We OVERRIDE it to provide a human-readable representation.
    // This is called automatically when you print the object:
    //   System.out.println(item);  → calls item.toString()
    // ============================================================

    /**
     * Returns a formatted string like:
     * "[ID: 3] Paneer Tikka (Starters) - ₹220.0"
     */
    @Override
    public String toString() {
        return "[ID: " + itemId + "] " + name + " (" + category + ") - ₹" + price;
    }
}
