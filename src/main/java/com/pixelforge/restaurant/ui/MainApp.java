package com.pixelforge.restaurant.ui;

// PixelForge Restaurant Management System - Main Entry Point

import com.pixelforge.restaurant.dao.BillDAO;
import com.pixelforge.restaurant.dao.DBConnection;
import com.pixelforge.restaurant.dao.MenuDAO;
import com.pixelforge.restaurant.dao.TableDAO;
import com.pixelforge.restaurant.exception.InvalidQuantityException;
import com.pixelforge.restaurant.exception.ItemNotFoundException;
import com.pixelforge.restaurant.exception.TableNotAvailableException;
import com.pixelforge.restaurant.model.Bill;
import com.pixelforge.restaurant.model.MenuItem;
import com.pixelforge.restaurant.model.OrderItem;
import com.pixelforge.restaurant.model.RestaurantTable;
import com.pixelforge.restaurant.service.analytics.BillingAndAnalyticsService;
import com.pixelforge.restaurant.service.table.TableAndOrderService;
import com.pixelforge.restaurant.thread.TableTimerMonitor;
import com.pixelforge.restaurant.util.CacheStore;
import com.pixelforge.restaurant.util.ReceiptExporter;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║                     MainApp.java                               ║
 * ║            PixelForge Restaurant Management System             ║
 * ║                   ~ Team Leader's File ~                       ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * ROLE OF THIS CLASS:
 * --------------------
 * This is the APPLICATION ENTRY POINT and sole UI orchestration layer.
 * It wires together all subsystems (DAO, Service, Thread, Util) and
 * drives the entire user experience via a console-based Scanner menu.
 *
 * DESIGN PHILOSOPHY — SEPARATION OF CONCERNS:
 * ---------------------------------------------
 * ┌────────────────┬────────────────────────────────────────────────┐
 * │  MainApp       │  Console I/O, menu loops, user input           │
 * │  Service Layer │  Business rules (order calc, table lifecycle)  │
 * │  DAO Layer     │  Pure JDBC database queries                    │
 * │  Thread Layer  │  Background table-time monitoring              │
 * │  Util Layer    │  File I/O (CSV, receipt export)                │
 * └────────────────┴────────────────────────────────────────────────┘
 * MainApp knows about all layers but orchestrates — it does NOT
 * contain SQL, file I/O logic, or business calculations directly.
 *
 * INPUT VALIDATION PATTERN (used throughout this file):
 * ------------------------------------------------------
 * Every numeric menu choice uses this pattern:
 *
 *   try {
 *       int choice = Integer.parseInt(scanner.nextLine().trim());
 *       // process choice
 *   } catch (NumberFormatException e) {
 *       System.out.println("Invalid input. Please enter a number.");
 *   }
 *
 * Custom exceptions (TableNotAvailableException, ItemNotFoundException,
 * InvalidQuantityException) are caught where relevant and shown to the
 * user with helpful, contextual messages — not raw stack traces.
 *
 * MULTI-CATCH SYNTAX (Java 7+):
 *   catch (TableNotAvailableException | ItemNotFoundException e) { ... }
 * Catches multiple exception types in one block when handling is identical.
 *
 * SESSION TRACKING:
 * -----------------
 * CacheStore<List<OrderItem>> sessionOrderItems tracks all order-item
 * lists for every table served during this session. This enables
 * the daily sales report to aggregate session-wide statistics without
 * needing another DB query (in-memory analytics).
 */
public class MainApp {

    /** Session bill counter for CacheStore integer keys */
    private static int sessionBillCounter = 1;

    // ================================================================
    // APPLICATION ENTRY POINT
    // ================================================================

    /**
     * main() — The JVM calls this method to start the application.
     *
     * Startup sequence:
     *   1. Print the PixelForge ASCII art banner
     *   2. Test DB connection (fail fast if DB is unreachable)
     *   3. Instantiate all DAOs and Services
     *   4. Start the background TableTimerMonitor thread
     *   5. Load tables from DB into memory via service layer
     *   6. Enter the main menu loop
     *
     * @param args Command-line arguments (not used in this application)
     */
    public static void main(String[] args) {
        printBanner();

        // ── Step 1: Test Database Connectivity ──────────────────────
        System.out.println("  Connecting to PixelForge database...");
        try {
            Connection testConn = DBConnection.getConnection();
            if (testConn != null && !testConn.isClosed()) {
                System.out.println("  ✔ Database connection established successfully!");
            }
        } catch (Exception e) {
            // If DB is unavailable, exit gracefully with a helpful message
            System.err.println("  ✘ FATAL: Could not connect to database!");
            System.err.println("  Cause: " + e.getMessage());
            System.err.println("  Please ensure MySQL is running and schema is loaded.");
            System.err.println("  Run: schema.sql in your MySQL client, then restart.");
            return; // Exit main() — no point running without a DB
        }

        // ── Step 2: Instantiate All DAOs ────────────────────────────
        MenuDAO  menuDAO  = new MenuDAO();
        TableDAO tableDAO = new TableDAO();
        BillDAO  billDAO  = new BillDAO();

        // ── Step 3: Instantiate Service Layer ───────────────────────
        // Services receive DAOs via constructor injection (Dependency Injection pattern)
        TableAndOrderService      tableService     = new TableAndOrderService(tableDAO, menuDAO);
        BillingAndAnalyticsService analyticsService = new BillingAndAnalyticsService(billDAO);

        // ── Step 4: Session-wide order tracking (in-memory cache) ───
        // Generic type: CacheStore<List<OrderItem>> — stores lists of order items per table
        // Used for daily analytics report without re-querying the DB
        // sessionKey is an int counter incremented each time a bill is generated
        CacheStore<List<OrderItem>> sessionOrderItems = new CacheStore<>();

        // ── Step 5: Start Background Thread ─────────────────────────
        // TableTimerMonitor runs as a daemon thread watching occupied-table durations
        TableTimerMonitor.startMonitoring();
        System.out.println("  ✔ Table timer monitor started (background thread).");

        // ── Step 6: Load Tables from DB into Service Memory ─────────
        try {
            tableService.loadTables();
            System.out.println("  ✔ Restaurant tables loaded from database.");
        } catch (Exception e) {
            System.err.println("  ⚠ Warning: Could not load tables — " + e.getMessage());
        }

        printSeparator();

        // ── Step 7: Scanner for all console input ───────────────────
        // Single Scanner instance created here and passed to all submenus
        // (Never create multiple Scanners on System.in — causes input issues)
        Scanner scanner = new Scanner(System.in);

        // ── Step 8: Main Menu Loop ───────────────────────────────────
        boolean running = true;
        while (running) {
            printSeparator();
            System.out.println("         PixelForge Restaurant Management");
            printSeparator();
            System.out.println("  1. Menu Management");
            System.out.println("  2. Table Management");
            System.out.println("  3. Order Management");
            System.out.println("  4. Billing & Checkout");
            System.out.println("  5. Daily Sales Report");
            System.out.println("  6. Export / Import (CSV & Receipts)");
            System.out.println("  0. Exit");
            printSeparator();
            System.out.print("  Enter choice: ");

            try {
                int choice = Integer.parseInt(scanner.nextLine().trim());

                switch (choice) {
                    case 1 -> showMenuManagement(scanner, menuDAO, tableService);
                    case 2 -> showTableManagement(scanner, tableService);
                    case 3 -> showOrderManagement(scanner, tableService, menuDAO);
                    case 4 -> showBillingCheckout(scanner, tableService, analyticsService, billDAO, sessionOrderItems);
                    case 5 -> showDailyReport(analyticsService, sessionOrderItems);
                    case 6 -> showExportImport(scanner, menuDAO);
                    case 0 -> {
                        System.out.println("\n  Closing PixelForge Restaurant System...");
                        System.out.println("  Thank you! Have a great day! 🙏");
                        DBConnection.closeConnection();
                        running = false; // Break out of the while loop
                    }
                    default -> System.out.println("  ⚠ Invalid choice. Please select 0–6.");
                }

            } catch (NumberFormatException e) {
                // User typed letters or symbols instead of a number
                System.out.println("  ✘ Invalid input. Please enter a number (0–6).");
            }
        }

        scanner.close(); // Close scanner when application exits
    }

    // ================================================================
    // SUBMENU 1: MENU MANAGEMENT
    // ================================================================

    /**
     * Displays and handles the Menu Management submenu.
     * Operations: view, add, update, delete menu items.
     *
     * @param sc      Shared Scanner for user input
     * @param menuDAO DAO for menu_items CRUD operations
     * @param ts      TableAndOrderService (used for context, not directly here)
     */
    private static void showMenuManagement(Scanner sc, MenuDAO menuDAO, TableAndOrderService ts) {
        boolean back = false;

        while (!back) {
            printSeparator();
            System.out.println("         Menu Management");
            printSeparator();
            System.out.println("  1. View All Menu Items (sorted by category)");
            System.out.println("  2. Add New Menu Item");
            System.out.println("  3. Update Menu Item");
            System.out.println("  4. Delete Menu Item");
            System.out.println("  0. Back to Main Menu");
            printSeparator();
            System.out.print("  Enter choice: ");

            try {
                int choice = Integer.parseInt(sc.nextLine().trim());

                switch (choice) {
                    case 1 -> {
                        // ── View All Menu Items ─────────────────────
                        List<MenuItem> items = menuDAO.getAllMenuItems();
                        if (items.isEmpty()) {
                            System.out.println("  ⚠ No menu items found in database.");
                        } else {
                            printSeparator();
                            System.out.printf("  %-6s %-28s %-16s %s%n",
                                    "ID", "Name", "Category", "Price (₹)");
                            printSeparator();
                            // Sort by category for better readability
                            items.stream()
                                 .sorted((a, b) -> a.getCategory().compareToIgnoreCase(b.getCategory()))
                                 .forEach(item -> System.out.printf(
                                         "  %-6d %-28s %-16s %.2f%n",
                                         item.getItemId(),
                                         item.getName(),
                                         item.getCategory(),
                                         item.getPrice()));
                            printSeparator();
                            System.out.println("  Total Items: " + items.size());
                        }
                    }

                    case 2 -> {
                        // ── Add New Menu Item ───────────────────────
                        System.out.print("  Item Name     : ");
                        String name = sc.nextLine().trim();

                        System.out.print("  Category      : ");
                        String category = sc.nextLine().trim();

                        double price = 0;
                        boolean validPrice = false;
                        while (!validPrice) {
                            System.out.print("  Price (₹)     : ");
                            try {
                                price = Double.parseDouble(sc.nextLine().trim());
                                if (price <= 0) {
                                    System.out.println("  ⚠ Price must be greater than 0. Try again.");
                                } else {
                                    validPrice = true; // Exit the price input loop
                                }
                            } catch (NumberFormatException e) {
                                System.out.println("  ✘ Invalid price. Enter a numeric value (e.g., 220.50).");
                            }
                        }

                        if (name.isEmpty() || category.isEmpty()) {
                            System.out.println("  ✘ Name and Category cannot be empty.");
                        } else {
                            MenuItem newItem = new MenuItem(0, name, category, price); // 0 = auto-increment by DB
                            menuDAO.addMenuItem(newItem);
                            System.out.println("  ✔ Menu item '" + name + "' added successfully!");
                        }
                    }

                    case 3 -> {
                        // ── Update Menu Item ────────────────────────
                        System.out.print("  Enter Item ID to update: ");
                        try {
                            int itemId = Integer.parseInt(sc.nextLine().trim());
                            MenuItem existing = menuDAO.getMenuItemById(itemId); // throws ItemNotFoundException

                            System.out.println("  Current Name     : " + existing.getName());
                            System.out.println("  Current Category : " + existing.getCategory());
                            System.out.println("  Current Price    : ₹" + existing.getPrice());
                            System.out.println("  (Press Enter to keep existing value)");

                            System.out.print("  New Name     [" + existing.getName() + "]: ");
                            String newName = sc.nextLine().trim();
                            if (newName.isEmpty()) newName = existing.getName();

                            System.out.print("  New Category [" + existing.getCategory() + "]: ");
                            String newCat = sc.nextLine().trim();
                            if (newCat.isEmpty()) newCat = existing.getCategory();

                            System.out.print("  New Price    [" + existing.getPrice() + "]: ");
                            String priceInput = sc.nextLine().trim();
                            double newPrice = priceInput.isEmpty()
                                    ? existing.getPrice()
                                    : Double.parseDouble(priceInput);

                            if (newPrice <= 0) {
                                System.out.println("  ✘ Price must be greater than 0. Update cancelled.");
                            } else {
                                MenuItem updated = new MenuItem(itemId, newName, newCat, newPrice);
                                menuDAO.updateMenuItem(updated);
                                System.out.println("  ✔ Item ID " + itemId + " updated successfully!");
                            }

                        } catch (NumberFormatException e) {
                            System.out.println("  ✘ Invalid ID or price. Please enter numeric values.");
                        } catch (ItemNotFoundException e) {
                            System.out.println("  ✘ Error: " + e.getMessage());
                        }
                    }

                    case 4 -> {
                        // ── Delete Menu Item ────────────────────────
                        System.out.print("  Enter Item ID to delete: ");
                        try {
                            int itemId = Integer.parseInt(sc.nextLine().trim());
                            MenuItem toDelete = menuDAO.getMenuItemById(itemId); // throws ItemNotFoundException

                            System.out.println("  Item to delete: " + toDelete.getName()
                                    + " (" + toDelete.getCategory() + ") — ₹" + toDelete.getPrice());
                            System.out.print("  Confirm delete? (Y/N): ");
                            String confirm = sc.nextLine().trim().toUpperCase();

                            if (confirm.equals("Y")) {
                                menuDAO.deleteMenuItem(itemId);
                                System.out.println("  ✔ Item '" + toDelete.getName() + "' deleted.");
                            } else {
                                System.out.println("  ℹ Delete cancelled.");
                            }

                        } catch (NumberFormatException e) {
                            System.out.println("  ✘ Invalid ID. Please enter a number.");
                        } catch (ItemNotFoundException e) {
                            System.out.println("  ✘ Error: " + e.getMessage());
                        }
                    }

                    case 0 -> back = true; // Return to main menu

                    default -> System.out.println("  ⚠ Invalid choice. Enter 0–4.");
                }

            } catch (NumberFormatException e) {
                System.out.println("  ✘ Invalid input. Please enter a number.");
            } catch (Exception e) {
                System.out.println("  ✘ Operation/Database error: " + e.getMessage());
            }

            // Pause before redisplaying the submenu (unless going back)
            if (!back) {
                System.out.println("\n  Press Enter to continue...");
                sc.nextLine();
            }
        }
    }

    // ================================================================
    // SUBMENU 2: TABLE MANAGEMENT
    // ================================================================

    /**
     * Displays and handles the Table Management submenu.
     * Operations: view tables, assign table, view queue, seat from queue.
     *
     * Demonstrates:
     *   - catch TableNotAvailableException for occupied tables
     *   - Waiting queue management (addToWaitingQueue, getNextInQueue)
     *
     * @param sc TableAndOrderService for all table operations
     * @param ts TableAndOrderService containing table state and queue
     */
    private static void showTableManagement(Scanner sc, TableAndOrderService ts) {
        boolean back = false;

        while (!back) {
            printSeparator();
            System.out.println("         Table Management");
            printSeparator();
            System.out.println("  1. View All Tables");
            System.out.println("  2. Assign Table to Customer");
            System.out.println("  3. View Waiting Queue");
            System.out.println("  4. Seat Next Customer from Queue");
            System.out.println("  0. Back to Main Menu");
            printSeparator();
            System.out.print("  Enter choice: ");

            try {
                int choice = Integer.parseInt(sc.nextLine().trim());

                switch (choice) {
                    case 1 -> {
                        // ── View All Tables ─────────────────────────
                        List<RestaurantTable> tables = ts.getAllTables();
                        printSeparator();
                        System.out.printf("  %-8s %-12s %-10s %s%n",
                                "TableID", "Capacity", "Status", "Occupied Since");
                        printSeparator();
                        for (RestaurantTable t : tables) {
                            String since = (t.getOccupiedSince() != null)
                                    ? t.getOccupiedSince().toString() : "—";
                            System.out.printf("  %-8d %-12d %-10s %s%n",
                                    t.getTableId(),
                                    t.getCapacity(),
                                    t.getStatus(),
                                    since);
                        }
                        printSeparator();
                        long available = tables.stream()
                                .filter(t -> "AVAILABLE".equals(t.getStatus())).count();
                        System.out.println("  Available: " + available + " / " + tables.size());
                    }

                    case 2 -> {
                        // ── Assign Table to Customer ─────────────────
                        System.out.print("  Enter Table ID   : ");
                        try {
                            int tableId = Integer.parseInt(sc.nextLine().trim());
                            System.out.print("  Customer Name    : ");
                            String customerName = sc.nextLine().trim();

                            if (customerName.isEmpty()) {
                                System.out.println("  ✘ Customer name cannot be empty.");
                                break;
                            }

                            // assignTable() throws TableNotAvailableException if occupied
                            ts.assignTable(tableId, customerName);
                            System.out.println("  ✔ Table " + tableId + " assigned to " + customerName + ".");
                            TableTimerMonitor.tableOccupied(tableId); // Start the timer for this table

                        } catch (NumberFormatException e) {
                            System.out.println("  ✘ Invalid Table ID. Please enter a number.");
                        } catch (TableNotAvailableException e) {
                            // ─── KEY: Custom exception caught here ──────────────────
                            // Instead of a cryptic crash, we show a friendly message
                            // and offer an alternative (waiting queue)
                            System.out.println("  ✘ " + e.getMessage());
                            System.out.print("  Table is occupied! Add to waiting list? (Y/N): ");
                            String addToQueue = sc.nextLine().trim().toUpperCase();
                            if (addToQueue.equals("Y")) {
                                System.out.print("  Customer Name for Queue: ");
                                String queueName = sc.nextLine().trim();
                                if (!queueName.isEmpty()) {
                                    ts.addToWaitingQueue(queueName);
                                    System.out.println("  ✔ " + queueName + " added to the waiting queue.");
                                } else {
                                    System.out.println("  ✘ Name cannot be empty. Not added to queue.");
                                }
                            } else {
                                System.out.println("  ℹ Customer not added to waiting list.");
                            }
                        }
                    }

                    case 3 -> {
                        // ── View Waiting Queue ──────────────────────
                        List<String> queue = ts.getWaitingQueue();
                        if (queue.isEmpty()) {
                            System.out.println("  ℹ Waiting queue is empty.");
                        } else {
                            System.out.println("  Waiting Queue (" + queue.size() + " customer(s)):");
                            for (int i = 0; i < queue.size(); i++) {
                                System.out.println("    " + (i + 1) + ". " + queue.get(i));
                            }
                        }
                    }

                    case 4 -> {
                        // ── Seat Next Customer from Queue ───────────
                        String nextCustomer = ts.peekNextInQueue();
                        if (nextCustomer == null) {
                            System.out.println("  ℹ No customers in the waiting queue.");
                        } else {
                            System.out.println("  Next customer in queue: " + nextCustomer);
                            System.out.print("  Enter Table ID to seat them: ");
                            try {
                                int tableId = Integer.parseInt(sc.nextLine().trim());
                                ts.seatNextFromQueue(tableId); // Removes from queue + assigns table
                                System.out.println("  ✔ " + nextCustomer
                                        + " seated at Table " + tableId + ".");
                                TableTimerMonitor.tableOccupied(tableId);
                            } catch (NumberFormatException e) {
                                System.out.println("  ✘ Invalid Table ID.");
                            } catch (TableNotAvailableException e) {
                                System.out.println("  ✘ " + e.getMessage()
                                        + " — Customer remains at top of queue.");
                            }
                        }
                    }

                    case 0 -> back = true;

                    default -> System.out.println("  ⚠ Invalid choice. Enter 0–4.");
                }

            } catch (NumberFormatException e) {
                System.out.println("  ✘ Invalid input. Please enter a number.");
            } catch (Exception e) {
                System.out.println("  ✘ Operation error: " + e.getMessage());
            }

            if (!back) {
                System.out.println("\n  Press Enter to continue...");
                sc.nextLine();
            }
        }
    }

    // ================================================================
    // SUBMENU 3: ORDER MANAGEMENT
    // ================================================================

    /**
     * Displays and handles the Order Management submenu.
     * Operations: place order, view order, update quantity, remove item.
     *
     * Demonstrates:
     *   - catch ItemNotFoundException when item not in menu
     *   - catch InvalidQuantityException when qty <= 0
     *   - Multi-catch syntax: catch (Ex1 | Ex2 e)
     *
     * @param sc       Shared Scanner
     * @param ts       TableAndOrderService for order state management
     * @param menuDAO  MenuDAO for fetching menu items
     */
    private static void showOrderManagement(Scanner sc, TableAndOrderService ts, MenuDAO menuDAO) {
        boolean back = false;

        while (!back) {
            printSeparator();
            System.out.println("         Order Management");
            printSeparator();
            System.out.println("  1. Place / Add Item to Order");
            System.out.println("  2. View Current Order for Table");
            System.out.println("  3. Update Item Quantity in Order");
            System.out.println("  4. Remove Item from Order");
            System.out.println("  0. Back to Main Menu");
            printSeparator();
            System.out.print("  Enter choice: ");

            try {
                int choice = Integer.parseInt(sc.nextLine().trim());

                switch (choice) {
                    case 1 -> {
                        // ── Place / Add Item to Order ─────────────────
                        System.out.print("  Enter Table ID: ");
                        try {
                            int tableId = Integer.parseInt(sc.nextLine().trim());

                            // Show the full menu so the user can pick an item
                            System.out.println("\n  --- Current Menu ---");
                            List<MenuItem> menuItems = menuDAO.getAllMenuItems();
                            if (menuItems.isEmpty()) {
                                System.out.println("  ⚠ No menu items available. Add items via Menu Management.");
                                break;
                            }
                            menuItems.stream()
                                     .sorted((a, b) -> a.getCategory().compareToIgnoreCase(b.getCategory()))
                                     .forEach(item -> System.out.printf(
                                             "    [%d] %-26s %-16s ₹%.2f%n",
                                             item.getItemId(), item.getName(),
                                             item.getCategory(), item.getPrice()));
                            printSeparator();

                            System.out.print("  Enter Item ID: ");
                            int itemId = Integer.parseInt(sc.nextLine().trim());

                            System.out.print("  Enter Quantity: ");
                            int qty = Integer.parseInt(sc.nextLine().trim());

                            // addItemToOrder() throws ItemNotFoundException if item doesn't exist
                            //                   throws InvalidQuantityException if qty <= 0
                            ts.addItemToOrder(tableId, itemId, qty);
                            System.out.println("  ✔ Item added to order for Table " + tableId + ".");

                        } catch (NumberFormatException e) {
                            System.out.println("  ✘ Invalid input. Table ID, Item ID, and Qty must be numbers.");
                        } catch (ItemNotFoundException e) {
                            // Item ID not found in menu_items table
                            System.out.println("  ✘ Error: " + e.getMessage());
                        } catch (InvalidQuantityException e) {
                            // Quantity was 0 or negative
                            System.out.println("  ✘ Error: " + e.getMessage());
                        }
                    }

                    case 2 -> {
                        // ── View Current Order for Table ─────────────
                        System.out.print("  Enter Table ID: ");
                        try {
                            int tableId = Integer.parseInt(sc.nextLine().trim());
                            List<OrderItem> order = ts.getOrderForTable(tableId);

                            if (order == null || order.isEmpty()) {
                                System.out.println("  ℹ No active order for Table " + tableId + ".");
                            } else {
                                printSeparator();
                                System.out.println("  Current Order — Table " + tableId);
                                printSeparator();
                                System.out.printf("  %-6s %-28s %-6s %-10s%n",
                                        "ItemID", "Name", "Qty", "Price (₹)");
                                printSeparator();
                                double running = 0;
                                for (OrderItem oi : order) {
                                    double lineTotal = oi.getMenuItem().getPrice() * oi.getQuantity();
                                    running += lineTotal;
                                    System.out.printf("  %-6d %-28s %-6d %.2f%n",
                                            oi.getMenuItem().getItemId(),
                                            oi.getMenuItem().getName(),
                                            oi.getQuantity(),
                                            lineTotal);
                                }
                                printSeparator();
                                System.out.printf("  %-40s %.2f%n", "Subtotal:", running);
                            }

                        } catch (NumberFormatException e) {
                            System.out.println("  ✘ Invalid Table ID.");
                        }
                    }

                    case 3 -> {
                        // ── Update Item Quantity ──────────────────────
                        System.out.print("  Enter Table ID : ");
                        try {
                            int tableId = Integer.parseInt(sc.nextLine().trim());
                            System.out.print("  Enter Item ID  : ");
                            int itemId = Integer.parseInt(sc.nextLine().trim());
                            System.out.print("  New Quantity   : ");
                            int newQty = Integer.parseInt(sc.nextLine().trim());

                            // throws ItemNotFoundException and/or InvalidQuantityException
                            ts.updateItemQuantity(tableId, itemId, newQty);
                            System.out.println("  ✔ Quantity updated for Item " + itemId
                                    + " on Table " + tableId + ".");

                        } catch (NumberFormatException e) {
                            System.out.println("  ✘ Invalid input. All values must be numbers.");
                        } catch (ItemNotFoundException | InvalidQuantityException e) {
                            // MULTI-CATCH: same handling for both exceptions — cleaner than two blocks
                            System.out.println("  ✘ Error: " + e.getMessage());
                        }
                    }

                    case 4 -> {
                        // ── Remove Item from Order ────────────────────
                        System.out.print("  Enter Table ID : ");
                        try {
                            int tableId = Integer.parseInt(sc.nextLine().trim());
                            System.out.print("  Enter Item ID to remove: ");
                            int itemId = Integer.parseInt(sc.nextLine().trim());

                            ts.removeItemFromOrder(tableId, itemId);
                            System.out.println("  ✔ Item " + itemId
                                    + " removed from order at Table " + tableId + ".");

                        } catch (NumberFormatException e) {
                            System.out.println("  ✘ Invalid input. Enter numeric IDs.");
                        } catch (ItemNotFoundException e) {
                            System.out.println("  ✘ Error: " + e.getMessage());
                        }
                    }

                    case 0 -> back = true;

                    default -> System.out.println("  ⚠ Invalid choice. Enter 0–4.");
                }

            } catch (NumberFormatException e) {
                System.out.println("  ✘ Invalid input. Please enter a number.");
            } catch (Exception e) {
                System.out.println("  ✘ Operation error: " + e.getMessage());
            }

            if (!back) {
                System.out.println("\n  Press Enter to continue...");
                sc.nextLine();
            }
        }
    }

    // ================================================================
    // SUBMENU 4: BILLING & CHECKOUT
    // ================================================================

    /**
     * Displays and handles the Billing & Checkout submenu.
     * Core Operation: Generate Bill → Save to DB (atomic transaction) →
     *                 Free Table → Export Receipt → Offer next customer seating.
     *
     * Demonstrates:
     *   - Using BillingAndAnalyticsService for all financial calculations
     *   - JDBC transaction: billDAO.saveBillAndFreeTable() (commit/rollback)
     *   - File I/O: ReceiptExporter.exportBillToFile()
     *   - Thread interaction: TableTimerMonitor.tableFreed()
     *   - Session tracking: sessionOrderItems for daily analytics
     *
     * @param sc           Shared Scanner
     * @param ts           TableAndOrderService
     * @param as           BillingAndAnalyticsService for calculations
     * @param billDAO      BillDAO for atomic bill persistence
     * @param sessionItems CacheStore tracking all billed order-item lists
     */
    private static void showBillingCheckout(
            Scanner sc,
            TableAndOrderService ts,
            BillingAndAnalyticsService as,
            BillDAO billDAO,
            CacheStore<List<OrderItem>> sessionItems) {

        boolean back = false;
        // Session key counter: each billed table gets a unique int key in sessionItems
        // We use a simple auto-incrementing int (not tableId) so multiple bills from
        // the same table across a session are all stored separately.
        // (Instance field replaced by static counter below; this is fine for a single-session app)

        while (!back) {
            printSeparator();
            System.out.println("         Billing & Checkout");
            printSeparator();
            System.out.println("  1. Generate Bill for Table");
            System.out.println("  0. Back to Main Menu");
            printSeparator();
            System.out.print("  Enter choice: ");

            try {
                int choice = Integer.parseInt(sc.nextLine().trim());

                switch (choice) {
                    case 1 -> {
                        // ── Generate Bill for Table ───────────────────
                        System.out.print("  Enter Table ID: ");
                        try {
                            int tableId = Integer.parseInt(sc.nextLine().trim());
                            List<OrderItem> orderItems = ts.getOrderForTable(tableId);

                            if (orderItems == null || orderItems.isEmpty()) {
                                System.out.println("  ✘ No active order found for Table " + tableId
                                        + ". Cannot generate bill.");
                                break;
                            }

                            // ── Step 1: Calculate financials using service ──
                            double subtotal   = as.calculateSubtotal(orderItems);
                            double gst        = as.calculateGST(subtotal);
                            double grandTotal = as.calculateGrandTotal(subtotal, gst);

                            // ── Step 2: Build Bill details string for DB storage ──
                            StringBuilder billDetails = new StringBuilder();
                            billDetails.append(String.format("%-6s %-26s %-6s %-10s%n",
                                    "ItemID", "Name", "Qty", "Amount(₹)"));
                            billDetails.append("--------------------------------------------------\n");
                            for (OrderItem oi : orderItems) {
                                double lineTotal = oi.getMenuItem().getPrice() * oi.getQuantity();
                                billDetails.append(String.format("%-6d %-26s %-6d %.2f%n",
                                        oi.getMenuItem().getItemId(),
                                        oi.getMenuItem().getName(),
                                        oi.getQuantity(),
                                        lineTotal));
                            }

                            // ── Step 3: Create Bill object ──────────────────
                            Bill bill = new Bill(0, tableId, billDetails.toString(),
                                    subtotal, gst, grandTotal, java.time.LocalDateTime.now());

                            // ── Step 4: Print itemized bill to console ──────
                            printSeparator();
                            System.out.println("         BILL — Table " + tableId);
                            printSeparator();
                            System.out.print(billDetails);
                            printSeparator();
                            System.out.printf("  %-36s ₹ %.2f%n", "Subtotal:", subtotal);
                            System.out.printf("  %-36s ₹ %.2f%n", "GST (5%):", gst);
                            System.out.printf("  %-36s ₹ %.2f%n", "GRAND TOTAL:", grandTotal);
                            printSeparator();

                            // ── Step 5: Save Bill to DB (ATOMIC TRANSACTION) ──
                            // saveBillAndFreeTable() does:
                            //   BEGIN TRANSACTION
                            //     INSERT into bills
                            //     UPDATE restaurant_tables SET status='AVAILABLE'
                            //   COMMIT
                            // On failure: ROLLBACK (data integrity preserved!)
                            try {
                                billDAO.saveBillAndFreeTable(bill, tableId);
                                System.out.println("  ✔ Bill saved to database. Table " + tableId + " is now FREE.");
                            } catch (Exception e) {
                                // Transaction was rolled back inside billDAO
                                System.err.println("  ✘ DB Error (transaction rolled back): " + e.getMessage());
                                System.out.println("  ⚠ Bill NOT saved. Table status unchanged. Please retry.");
                                break; // Don't proceed with checkout if DB save failed
                            }

                            // ── Step 6: Export receipt to file ──────────────
                            ReceiptExporter.exportBillToFile(bill, tableId);

                            // ── Step 7: Notify background thread ────────────
                            TableTimerMonitor.tableFreed(tableId); // Stop the timer for this table

                            // ── Step 8: Clear in-memory order ───────────────
                            // Store order items in session cache BEFORE clearing (for daily report)
                            sessionItems.put(sessionBillCounter++, new ArrayList<>(orderItems));
                            ts.clearOrder(tableId);

                            // ── Step 9: Offer to seat next waiting customer ──
                            String nextCustomer = ts.peekNextInQueue();
                            if (nextCustomer != null) {
                                System.out.println("\n  ℹ Next customer in queue: " + nextCustomer);
                                System.out.print("  Seat next customer from queue at Table "
                                        + tableId + "? (Y/N): ");
                                String seat = sc.nextLine().trim().toUpperCase();
                                if (seat.equals("Y")) {
                                    try {
                                        ts.seatNextFromQueue(tableId);
                                        System.out.println("  ✔ " + nextCustomer
                                                + " seated at Table " + tableId + ".");
                                        TableTimerMonitor.tableOccupied(tableId);
                                    } catch (TableNotAvailableException e) {
                                        System.out.println("  ✘ " + e.getMessage());
                                    }
                                }
                            }

                        } catch (NumberFormatException e) {
                            System.out.println("  ✘ Invalid Table ID. Please enter a number.");
                        }
                    }

                    case 0 -> back = true;

                    default -> System.out.println("  ⚠ Invalid choice. Enter 0 or 1.");
                }

            } catch (NumberFormatException e) {
                System.out.println("  ✘ Invalid input. Please enter a number.");
            } catch (Exception e) {
                System.out.println("  ✘ Operation error: " + e.getMessage());
            }

            if (!back) {
                System.out.println("\n  Press Enter to continue...");
                sc.nextLine();
            }
        }
    }

    // ================================================================
    // SUBMENU 5: DAILY SALES REPORT
    // ================================================================

    /**
     * Displays the daily sales report for the current session.
     * Delegates all analytics calculation to BillingAndAnalyticsService.
     *
     * The sessionItems CacheStore holds all order-item lists from every
     * billed table in this session — no DB query required for in-memory totals.
     *
     * @param as           BillingAndAnalyticsService for report generation
     * @param sessionItems In-memory session order items cache
     */
    private static void showDailyReport(
            BillingAndAnalyticsService as,
            CacheStore<List<OrderItem>> sessionItems) {

        printSeparator();
        System.out.println("         Daily Sales Report — Current Session");
        printSeparator();

        // Aggregate all session order items into one flat list for the report
        List<OrderItem> allItems = new ArrayList<>();
        for (List<OrderItem> orderList : sessionItems.getAll()) {
            allItems.addAll(orderList);
        }

        if (allItems.isEmpty()) {
            System.out.println("  ℹ No bills generated in this session yet.");
            System.out.println("  Complete at least one billing to see the report.");
        } else {
            // Delegate report printing to the analytics service
            as.printDailySalesReport(allItems);
        }

        printSeparator();
    }

    // ================================================================
    // SUBMENU 6: EXPORT / IMPORT
    // ================================================================

    /**
     * Displays and handles the Export/Import submenu.
     * Operations: export menu to CSV, import menu from CSV.
     *
     * File I/O is handled entirely by ReceiptExporter (util layer).
     * MainApp only handles user input and passes data between layers.
     *
     * @param sc      Shared Scanner
     * @param menuDAO MenuDAO for fetching menu items (export) and saving (import)
     */
    private static void showExportImport(Scanner sc, MenuDAO menuDAO) {
        boolean back = false;

        while (!back) {
            printSeparator();
            System.out.println("         Export / Import");
            printSeparator();
            System.out.println("  1. Export Menu to CSV (receipts/menu_backup.csv)");
            System.out.println("  2. Import Menu from CSV (enter file path)");
            System.out.println("  0. Back to Main Menu");
            printSeparator();
            System.out.print("  Enter choice: ");

            try {
                int choice = Integer.parseInt(sc.nextLine().trim());

                switch (choice) {
                    case 1 -> {
                        // ── Export Menu to CSV ────────────────────────
                        List<MenuItem> items = menuDAO.getAllMenuItems();
                        if (items.isEmpty()) {
                            System.out.println("  ⚠ No menu items to export.");
                        } else {
                            ReceiptExporter.exportMenuToCSV(items);
                        }
                    }

                    case 2 -> {
                        // ── Import Menu from CSV ──────────────────────
                        System.out.print("  Enter CSV file path: ");
                        String filePath = sc.nextLine().trim();

                        if (filePath.isEmpty()) {
                            System.out.println("  ✘ File path cannot be empty.");
                            break;
                        }

                        List<MenuItem> imported = ReceiptExporter.importMenuFromCSV(filePath);

                        if (imported.isEmpty()) {
                            System.out.println("  ⚠ No items imported (file empty or all lines were invalid).");
                        } else {
                            System.out.println("  Parsed " + imported.size() + " items from CSV.");
                            System.out.print("  Save all imported items to database? (Y/N): ");
                            String confirm = sc.nextLine().trim().toUpperCase();

                            if (confirm.equals("Y")) {
                                int savedCount = 0;
                                int errorCount = 0;
                                for (MenuItem item : imported) {
                                    try {
                                        // Insert as new item (ignore original itemId — DB auto-assigns)
                                        MenuItem toSave = new MenuItem(0, item.getName(),
                                                item.getCategory(), item.getPrice());
                                        menuDAO.addMenuItem(toSave);
                                        savedCount++;
                                    } catch (Exception e) {
                                        System.err.println("  ⚠ Failed to save item '"
                                                + item.getName() + "': " + e.getMessage());
                                        errorCount++;
                                    }
                                }
                                System.out.println("  ✔ Saved: " + savedCount + " items.");
                                if (errorCount > 0) {
                                    System.out.println("  ✘ Failed: " + errorCount + " items.");
                                }
                            } else {
                                System.out.println("  ℹ Import cancelled. No changes made to database.");
                            }
                        }
                    }

                    case 0 -> back = true;

                    default -> System.out.println("  ⚠ Invalid choice. Enter 0–2.");
                }

            } catch (NumberFormatException e) {
                System.out.println("  ✘ Invalid input. Please enter a number.");
            } catch (Exception e) {
                System.out.println("  ✘ Operation error: " + e.getMessage());
            }

            if (!back) {
                System.out.println("\n  Press Enter to continue...");
                sc.nextLine();
            }
        }
    }

    // ================================================================
    // UTILITY / DISPLAY HELPERS
    // ================================================================

    /**
     * Prints the PixelForge Restaurant ASCII art banner.
     * Called once at application startup for a polished first impression.
     */
    private static void printBanner() {
        System.out.println();
        System.out.println("  ██████╗ ██╗██╗  ██╗███████╗██╗     ███████╗ ██████╗ ██████╗  ██████╗ ███████╗");
        System.out.println("  ██╔══██╗██║╚██╗██╔╝██╔════╝██║     ██╔════╝██╔═══██╗██╔══██╗██╔════╝ ██╔════╝");
        System.out.println("  ██████╔╝██║ ╚███╔╝ █████╗  ██║     █████╗  ██║   ██║██████╔╝██║  ███╗█████╗  ");
        System.out.println("  ██╔═══╝ ██║ ██╔██╗ ██╔══╝  ██║     ██╔══╝  ██║   ██║██╔══██╗██║   ██║██╔══╝  ");
        System.out.println("  ██║     ██║██╔╝ ██╗███████╗███████╗██║     ╚██████╔╝██║  ██║╚██████╔╝███████╗");
        System.out.println("  ╚═╝     ╚═╝╚═╝  ╚═╝╚══════╝╚══════╝╚═╝      ╚═════╝ ╚═╝  ╚═╝ ╚═════╝ ╚══════╝");
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("  ║          Restaurant Management & Billing System  v1.0                   ║");
        System.out.println("  ║               Developed by: PixelForge Team                            ║");
        System.out.println("  ║         ~ Fine Dining | Smart Billing | Seamless Experience ~           ║");
        System.out.println("  ╚══════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }

    /**
     * Prints a horizontal separator line for visual structure in the console UI.
     * Used consistently to delineate sections, tables, and summaries.
     */
    private static void printSeparator() {
        System.out.println("  ════════════════════════════════════════════════");
    }
}
