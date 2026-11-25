package com.pixelforge.restaurant.service.table;

import com.pixelforge.restaurant.dao.MenuDAO;
import com.pixelforge.restaurant.dao.TableDAO;
import com.pixelforge.restaurant.exception.InvalidQuantityException;
import com.pixelforge.restaurant.exception.ItemNotFoundException;
import com.pixelforge.restaurant.exception.TableNotAvailableException;
import com.pixelforge.restaurant.model.MenuItem;
import com.pixelforge.restaurant.model.Order;
import com.pixelforge.restaurant.model.OrderItem;
import com.pixelforge.restaurant.model.RestaurantTable;
import com.pixelforge.restaurant.thread.TableTimerMonitor;
import com.pixelforge.restaurant.util.CacheStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;

/**
 * ============================================================
 * SERVICE CLASS: TableAndOrderService
 * ============================================================
 * This class is the BRAIN of table management and live ordering.
 * It coordinates between the data layer (DAOs), the in-memory
 * cache (CacheStore, HashMap), and business rules.
 *
 * DATA STRUCTURES USED — and WHY:
 * -----------------------------------------
 *
 * 1) HashMap<Integer, RestaurantTable> tableCache
 *    --------------------------------------------------
 *    WHY HashMap instead of ArrayList?
 *
 *    ArrayList lookup (by tableId):  O(n) — have to loop through
 *    every element until we find the right one. Slow for large lists.
 *
 *    HashMap lookup (by tableId):    O(1) — direct key-to-value
 *    mapping, like a dictionary. No matter how many tables there are,
 *    the lookup takes roughly the same amount of time.
 *
 *    Since we constantly check/update individual tables by their ID,
 *    HashMap gives us a significant performance advantage.
 *
 * 2) Queue<String> waitingQueue (backed by LinkedList)
 *    --------------------------------------------------
 *    A Queue is a FIFO (First In, First Out) data structure.
 *    When more customers arrive than available tables, they are
 *    added to the waitingQueue. The FIRST customer who joined
 *    the queue gets the FIRST available table — fair and orderly.
 *
 *    LinkedList is used as the underlying implementation because
 *    it supports efficient add-at-tail and remove-from-head.
 *
 * 3) CacheStore<Order> activeOrders
 *    --------------------------------------------------
 *    Our generic utility cache (backed by HashMap internally).
 *    Stores the live Order for each occupied table in memory.
 *    When a table is billed and cleared, its order is removed.
 *    This avoids hitting the DB for every item addition during
 *    an active dining session.
 *
 * DEPENDENCY INJECTION:
 * -----------------------------------------
 * Instead of creating TableDAO and MenuDAO inside this class,
 * we RECEIVE them via the constructor. This is called Dependency
 * Injection — it makes the class easier to test and configure.
 * ============================================================
 */
public class TableAndOrderService {

    // ----------------------------------------------------------------
    // FIELDS
    // ----------------------------------------------------------------

    /**
     * In-memory fast-lookup table store.
     * Key   = tableId (Integer)
     * Value = RestaurantTable object
     *
     * O(1) lookup — much faster than searching a List every time.
     */
    private Map<Integer, RestaurantTable> tableCache;

    /**
     * Customer waiting queue.
     * When all tables are occupied, new customers are added here.
     * Uses FIFO ordering — first customer to wait gets served first.
     * LinkedList implements the Queue interface efficiently.
     */
    private Queue<String> waitingQueue;

    /**
     * Active (in-progress) orders indexed by tableId.
     * CacheStore<Order> is our generic in-memory cache utility.
     * Orders live here from the moment a table is assigned until
     * the bill is generated and the table is freed.
     */
    private CacheStore<Order> activeOrders;

    /**
     * DAO for database operations on the restaurant_tables table.
     * Injected via constructor (Dependency Injection).
     */
    private TableDAO tableDAO;

    /**
     * DAO for database operations on the menu_items table.
     * Injected via constructor (Dependency Injection).
     */
    private MenuDAO menuDAO;

    // ----------------------------------------------------------------
    // CONSTRUCTOR — Dependency Injection
    // ----------------------------------------------------------------

    /**
     * Creates a TableAndOrderService with the required DAOs injected.
     * Initialises all in-memory data structures.
     *
     * @param tableDAO DAO for restaurant table DB operations
     * @param menuDAO  DAO for menu item DB operations
     */
    public TableAndOrderService(TableDAO tableDAO, MenuDAO menuDAO) {
        this.tableDAO     = tableDAO;
        this.menuDAO      = menuDAO;
        this.tableCache   = new HashMap<>();        // Fast O(1) table lookups
        this.waitingQueue = new LinkedList<>();     // FIFO customer waitlist
        this.activeOrders = new CacheStore<>();     // Generic in-memory order cache
    }

    // ----------------------------------------------------------------
    // TABLE LOADING
    // ----------------------------------------------------------------

    /**
     * Loads ALL restaurant tables from the database into the in-memory
     * tableCache HashMap.
     *
     * Call this method ONCE at application startup. After that, all
     * table reads happen from RAM (the HashMap), which is much faster
     * than querying the DB for every single table lookup.
     *
     * We write-through to the DB on updates (e.g., assignTable) to
     * keep the DB in sync, but reads always come from cache.
     */
    public void loadTables() {
        try {
            // 1. Fetch all tables from the database
            List<RestaurantTable> allTables = tableDAO.getAllTables();

            // 2. Clear any existing cache entries (safe re-load)
            tableCache.clear();

            // 3. Populate the HashMap — key = tableId, value = table object
            for (RestaurantTable table : allTables) {
                tableCache.put(table.getTableId(), table);
            }

            System.out.println("[TableAndOrderService] Loaded " + tableCache.size()
                + " tables into cache.");

        } catch (SQLException e) {
            System.err.println("[TableAndOrderService] ERROR loading tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ----------------------------------------------------------------
    // TABLE RETRIEVAL
    // ----------------------------------------------------------------

    /**
     * Returns a sorted list of ALL tables (sorted by tableId ascending).
     *
     * We pull from the in-memory tableCache (fast HashMap read),
     * then sort the resulting list by tableId for consistent display.
     *
     * Comparator.comparingInt(RestaurantTable::getTableId):
     *   This is a method reference — shorthand for:
     *   (t1, t2) -> Integer.compare(t1.getTableId(), t2.getTableId())
     *
     * @return Sorted list of all RestaurantTable objects
     */
    public List<RestaurantTable> getAllTables() {
        // Convert HashMap values to a List
        List<RestaurantTable> tables = new ArrayList<>(tableCache.values());

        // Sort by tableId so the UI always displays tables in order (1, 2, 3...)
        tables.sort(Comparator.comparingInt(RestaurantTable::getTableId));

        return tables;
    }

    /**
     * Returns all OCCUPIED tables (status == "OCCUPIED").
     *
     * We use stream().filter() to select only the occupied ones,
     * then collect the results back into a new ArrayList.
     *
     * @return List of currently occupied RestaurantTable objects
     */
    public List<RestaurantTable> getOccupiedTables() {
        return tableCache.values().stream()
            // filter: keep only tables where status is "OCCUPIED"
            .filter(table -> "OCCUPIED".equals(table.getStatus()))
            // collect the matching tables into a new List
            .collect(Collectors.toList());
    }

    /**
     * Returns all AVAILABLE tables (status == "AVAILABLE").
     *
     * @return List of currently available RestaurantTable objects
     */
    public List<RestaurantTable> getAvailableTables() {
        return tableCache.values().stream()
            .filter(table -> "AVAILABLE".equals(table.getStatus()))
            .collect(Collectors.toList());
    }

    // ----------------------------------------------------------------
    // MENU RETRIEVAL (sorted)
    // ----------------------------------------------------------------

    /**
     * Returns the full menu sorted by PRICE (ascending, cheapest first).
     *
     * Comparator.comparingDouble(MenuItem::getPrice):
     *   Creates a comparator that compares MenuItems by their price.
     *   This is equivalent to: (m1, m2) -> Double.compare(m1.getPrice(), m2.getPrice())
     *
     * @return Price-sorted list of all MenuItems
     */
    public List<MenuItem> getMenuSortedByPrice() {
        try {
            List<MenuItem> menu = menuDAO.getAllMenuItems();
            // Sort using a Comparator on the price field
            menu.sort(Comparator.comparingDouble(MenuItem::getPrice));
            return menu;
        } catch (SQLException e) {
            System.err.println("[TableAndOrderService] ERROR fetching menu: " + e.getMessage());
            return new ArrayList<>(); // Return empty list on failure (safe fallback)
        }
    }

    /**
     * Returns the full menu sorted by CATEGORY (alphabetical).
     *
     * Comparator.comparing(MenuItem::getCategory):
     *   Creates a comparator that compares MenuItems by their category String.
     *   String comparison is natural alphabetical order (A → Z).
     *
     * @return Category-sorted list of all MenuItems
     */
    public List<MenuItem> getMenuSortedByCategory() {
        try {
            List<MenuItem> menu = menuDAO.getAllMenuItems();
            // Sort alphabetically by category name
            menu.sort(Comparator.comparing(MenuItem::getCategory));
            return menu;
        } catch (SQLException e) {
            System.err.println("[TableAndOrderService] ERROR fetching menu: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ----------------------------------------------------------------
    // TABLE ASSIGNMENT
    // ----------------------------------------------------------------

    /**
     * Assigns a table to a customer — the central "seat a customer" operation.
     *
     * PROCESS:
     *   1. Look up the table in the in-memory tableCache (O(1) HashMap lookup).
     *   2. If the table is OCCUPIED, throw TableNotAvailableException.
     *   3. Otherwise:
     *      a. Update the table status to "OCCUPIED" in the DATABASE.
     *      b. Update the in-memory tableCache to reflect the new status.
     *      c. Start a TableTimerMonitor thread to track how long this table is occupied.
     *   4. Return true to indicate success.
     *
     * EXCEPTION HANDLING:
     *   We use a custom checked exception (TableNotAvailableException) so the
     *   caller (UI layer) is FORCED to handle the case where the table is busy.
     *   This is better than returning a boolean false and hoping the caller checks it.
     *
     * @param tableId      The ID of the table to assign
     * @param customerName The name of the customer being seated
     * @return             true if the table was successfully assigned
     * @throws TableNotAvailableException if the table is already occupied
     */
    public boolean assignTable(int tableId, String customerName)
            throws TableNotAvailableException {

        // Step 1: O(1) HashMap lookup — no looping needed!
        RestaurantTable table = tableCache.get(tableId);

        // Step 2: Table doesn't exist in cache (invalid ID)
        if (table == null) {
            throw new TableNotAvailableException(
                "Table #" + tableId + " does not exist."
            );
        }

        // Step 3: Table is already occupied — throw exception
        if ("OCCUPIED".equals(table.getStatus())) {
            throw new TableNotAvailableException(
                "Table #" + tableId + " is currently OCCUPIED. Cannot assign to "
                + customerName + "."
            );
        }

        try {
            // Step 4a: Persist the status change to the database
            // We pass a connection here so the DAO can update the DB record
            tableDAO.updateTableStatusOccupied(tableId, com.pixelforge.restaurant.dao.DBConnection.getConnection());

            // Step 4b: Update the in-memory cache to reflect the new status
            // This keeps RAM in sync with what we just wrote to the DB.
            table.setStatus("OCCUPIED");
            table.setOccupiedSince(java.time.LocalDateTime.now());
            tableCache.put(tableId, table); // Re-put to make sure cache is updated

            // Step 4c: Register the table with TableTimerMonitor (daemon background tracking)
            TableTimerMonitor.tableOccupied(tableId);

            System.out.println("[TableAndOrderService] Table #" + tableId
                + " assigned to customer: " + customerName);

            // Step 5: Return true to indicate success
            return true;

        } catch (SQLException e) {
            System.err.println("[TableAndOrderService] DB error assigning table #"
                + tableId + ": " + e.getMessage());
            e.printStackTrace();
            return false; // DB update failed; don't update cache
        }
    }

    // ----------------------------------------------------------------
    // WAITING QUEUE MANAGEMENT
    // ----------------------------------------------------------------

    /**
     * Adds a customer to the FIFO waiting queue when no table is available.
     *
     * Queue FIFO behaviour:
     *   - offer() / add() → adds to the TAIL of the queue
     *   - poll()          → removes from the HEAD of the queue
     *
     * The first customer to call addToWaitingQueue() will be the first
     * one returned by nextInQueue() when a table opens up.
     *
     * @param customerName The name of the customer to add to the waitlist
     */
    public void addToWaitingQueue(String customerName) {
        waitingQueue.offer(customerName); // offer() is preferred over add() — won't throw on overflow
        System.out.println("[TableAndOrderService] Customer '" + customerName
            + "' added to waiting queue. Queue size: " + waitingQueue.size());
        System.out.println("  Estimated position: #" + waitingQueue.size());
    }

    /**
     * Returns and removes the NEXT customer from the waiting queue (FIFO).
     * Returns null if the queue is empty (no one is waiting).
     *
     * This should be called when a table becomes available, so the
     * system can automatically seat the next waiting customer.
     *
     * @return The name of the next waiting customer, or null if queue is empty
     */
    public String nextInQueue() {
        // poll() removes and returns the HEAD, or returns null if queue is empty.
        // This is safer than remove(), which throws NoSuchElementException on empty queue.
        String nextCustomer = waitingQueue.poll();

        if (nextCustomer != null) {
            System.out.println("[TableAndOrderService] Next in queue: " + nextCustomer);
        } else {
            System.out.println("[TableAndOrderService] Waiting queue is empty.");
        }

        return nextCustomer;
    }

    /**
     * Returns a snapshot of the current waiting queue as a List.
     *
     * @return List of customer names in the queue
     */
    public List<String> getWaitingQueue() {
        return new ArrayList<>(waitingQueue);
    }

    /**
     * Inspects the next customer in the queue without removing them.
     *
     * @return The next customer's name, or null if the queue is empty
     */
    public String peekNextInQueue() {
        return waitingQueue.peek();
    }

    /**
     * Assigns the next customer waiting in the queue to the specified table.
     * Removes the customer from the queue only after successful assignment.
     *
     * @param tableId The ID of the table to assign
     * @throws TableNotAvailableException if the table is occupied or invalid
     */
    public void seatNextFromQueue(int tableId) throws TableNotAvailableException {
        String customer = waitingQueue.peek();
        if (customer == null) {
            System.out.println("[TableAndOrderService] Queue is empty, no customer to seat.");
            return;
        }

        boolean assigned = assignTable(tableId, customer);
        if (assigned) {
            waitingQueue.poll(); // Remove from queue now that they are seated
        }
    }

    // ----------------------------------------------------------------
    // ORDER MANAGEMENT (using CacheStore)
    // ----------------------------------------------------------------

    /**
     * Adds an item to the active order for a given table by looking up
     * the MenuItem from MenuDAO.
     *
     * @param tableId The table placing the order
     * @param itemId  The ID of the MenuItem to add
     * @param qty     The quantity to order (> 0)
     * @throws ItemNotFoundException if the item doesn't exist
     * @throws InvalidQuantityException if qty <= 0
     */
    public void addItemToOrder(int tableId, int itemId, int qty)
            throws ItemNotFoundException, InvalidQuantityException {
        if (qty <= 0) {
            throw new InvalidQuantityException("Quantity must be greater than zero. Received: " + qty);
        }

        try {
            MenuItem item = menuDAO.getMenuItemById(itemId);
            if (item == null) {
                throw new ItemNotFoundException("Menu item ID " + itemId + " not found.");
            }
            OrderItem orderItem = new OrderItem(item, qty);
            placeOrder(tableId, orderItem);
        } catch (SQLException e) {
            throw new ItemNotFoundException("Database error finding menu item ID " + itemId + ": " + e.getMessage());
        }
    }

    /**
     * Returns the list of OrderItems in the active order for a given table.
     *
     * @param tableId The table ID
     * @return List of OrderItems, or empty list if no order exists
     */
    public List<OrderItem> getOrderForTable(int tableId) {
        Order order = activeOrders.get(tableId);
        return (order != null) ? order.getItems() : new ArrayList<>();
    }

    /**
     * Updates the quantity of a specific item in the table's active order.
     *
     * @param tableId The table ID
     * @param itemId  The menu item ID
     * @param newQty  The new quantity (> 0)
     * @throws ItemNotFoundException if order or item does not exist
     * @throws InvalidQuantityException if newQty <= 0
     */
    public void updateItemQuantity(int tableId, int itemId, int newQty)
            throws ItemNotFoundException, InvalidQuantityException {
        if (newQty <= 0) {
            throw new InvalidQuantityException("Quantity must be greater than zero. Received: " + newQty);
        }

        Order order = activeOrders.get(tableId);
        if (order == null || order.getItems().isEmpty()) {
            throw new ItemNotFoundException("No active order found for table " + tableId);
        }

        boolean found = false;
        for (OrderItem oi : order.getItems()) {
            if (oi.getMenuItem().getItemId() == itemId) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new ItemNotFoundException("Item ID " + itemId + " is not in the order for table " + tableId);
        }

        order.updateQuantity(itemId, newQty);
    }

    /**
     * Removes an item from the active order for a given table.
     *
     * @param tableId The table ID
     * @param itemId  The menu item ID to remove
     * @throws ItemNotFoundException if order or item does not exist
     */
    public void removeItemFromOrder(int tableId, int itemId) throws ItemNotFoundException {
        Order order = activeOrders.get(tableId);
        if (order == null || order.getItems().isEmpty()) {
            throw new ItemNotFoundException("No active order found for table " + tableId);
        }

        boolean found = false;
        for (OrderItem oi : order.getItems()) {
            if (oi.getMenuItem().getItemId() == itemId) {
                found = true;
                break;
            }
        }

        if (!found) {
            throw new ItemNotFoundException("Item ID " + itemId + " is not in the order for table " + tableId);
        }

        order.removeItem(itemId);
    }

    /**
     * Adds an item to the active order for a given table.
     *
     * If no order exists yet for this table, a new Order is created first.
     * Orders are stored in the CacheStore<Order> (backed by HashMap internally).
     *
     * @param tableId The table placing the order
     * @param item    The OrderItem to add to the order
     */
    public void placeOrder(int tableId, OrderItem item) {
        // Check if an order already exists for this table in the cache
        Order order = activeOrders.get(tableId);

        if (order == null) {
            // No existing order — create a new one for this table
            order = new Order(tableId);
            activeOrders.put(tableId, order);
            System.out.println("[TableAndOrderService] New order started for Table #" + tableId);
        }

        // Add the item to the order (Order.addItem handles duplicate items gracefully)
        order.addItem(item);
        System.out.println("[TableAndOrderService] Added to Table #" + tableId + ": " + item);
    }

    /**
     * Returns the current active Order for a given table.
     * Returns null if no order has been placed for this table yet.
     *
     * @param tableId The table whose order to retrieve
     * @return        The active Order, or null if none exists
     */
    public Order getOrder(int tableId) {
        return activeOrders.get(tableId); // O(1) HashMap lookup via CacheStore
    }

    /**
     * Clears (removes) the active order for a given table.
     * This is called after a bill has been generated and the table is freed.
     *
     * @param tableId The table whose order to clear from the cache
     */
    public void clearOrder(int tableId) {
        activeOrders.remove(tableId);
        System.out.println("[TableAndOrderService] Order cleared for Table #" + tableId);
    }
}
