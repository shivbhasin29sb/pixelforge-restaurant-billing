package com.pixelforge.restaurant.thread;

/*
 * =============================================================================
 * TableTimerMonitor.java - Background Daemon Thread for Table Time Monitoring
 * =============================================================================
 *
 * ============================================================
 * UNDERSTANDING DAEMON THREADS
 * ============================================================
 *
 * In Java, every thread is either a USER thread or a DAEMON thread.
 *
 * USER THREAD:
 *   - The JVM (Java Virtual Machine) will NOT exit until ALL user threads finish.
 *   - Use for important tasks that must complete (e.g., saving data, handling requests).
 *
 * DAEMON THREAD:
 *   - The JVM CAN exit even if daemon threads are still running.
 *   - Think of them as "background service" threads - they exist to SUPPORT
 *     the main application, not to do critical work on their own.
 *   - When all user threads finish → JVM exits → all daemon threads are killed instantly.
 *   - Examples: Garbage Collector, IDE auto-save, background analytics.
 *
 * WHY IS TableTimerMonitor A DAEMON THREAD?
 *   - It runs in the background, periodically checking table occupancy.
 *   - When the restaurant app closes (main thread exits), we don't want
 *     this timer to keep the JVM alive indefinitely. It should die with the app.
 *   - Set via: executor.setDaemon(true) on the thread factory.
 *
 * ============================================================
 * UNDERSTANDING ScheduledExecutorService
 * ============================================================
 *
 * ScheduledExecutorService is the modern, recommended way to run tasks
 * periodically in Java. It replaces the old java.util.Timer class.
 *
 * Key method used:
 *   scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit)
 *   - task:         The code to run (our checkTables() method)
 *   - initialDelay: Wait this long before the FIRST run (60 seconds here)
 *   - period:       Wait this long between runs (120 seconds = 2 minutes)
 *   - unit:         The time unit (TimeUnit.SECONDS)
 *
 * Benefits over old java.util.Timer:
 *   - Thread-safe
 *   - Better exception handling (task continues after exceptions)
 *   - More flexible scheduling options
 *
 * ============================================================
 * UNDERSTANDING ConcurrentHashMap
 * ============================================================
 *
 * We use ConcurrentHashMap<Integer, LocalDateTime> to track occupied tables:
 *   - KEY:   Table ID (Integer) - e.g., 1, 2, 3
 *   - VALUE: LocalDateTime when the table became occupied
 *
 * WHY ConcurrentHashMap instead of regular HashMap?
 *   - Regular HashMap is NOT thread-safe. If two threads modify it at the
 *     same time, data can be corrupted (ConcurrentModificationException).
 *   - ConcurrentHashMap is thread-safe. Multiple threads can read/write
 *     simultaneously without corrupting the data.
 *   - The UI thread calls tableOccupied() / tableFreed()
 *   - The background scheduler thread calls checkTables()
 *   - Both access occupiedSince → we NEED thread safety → use ConcurrentHashMap
 *
 * ============================================================
 * UNDERSTANDING synchronized
 * ============================================================
 *
 * The 'synchronized' keyword ensures only ONE thread executes a block at a time.
 * When thread A is inside a synchronized block, thread B must WAIT until A exits.
 *
 * We use it in checkTables() as an extra safety layer when iterating over the map,
 * even though ConcurrentHashMap handles individual put/get safely. This prevents
 * the rare case where the map is modified WHILE we're iterating over it.
 *
 * =============================================================================
 */

import java.time.LocalDateTime;        // Stores date + time without timezone info
import java.time.temporal.ChronoUnit;  // Calculates time differences (e.g., minutes between two times)
import java.util.Map;                  // Map interface (used for entrySet() iteration)
import java.util.concurrent.ConcurrentHashMap;       // Thread-safe HashMap
import java.util.concurrent.Executors;               // Factory for creating ExecutorService instances
import java.util.concurrent.ScheduledExecutorService; // Runs tasks on a schedule
import java.util.concurrent.ThreadFactory;            // Creates threads with custom settings
import java.util.concurrent.TimeUnit;                 // Time units (SECONDS, MINUTES, etc.)

/**
 * TableTimerMonitor - A singleton-style utility class that uses a background
 * daemon thread to monitor how long restaurant tables have been occupied.
 *
 * If a table is occupied for longer than MAX_DINING_MINUTES (60 minutes),
 * an alert is printed to the console so staff can follow up.
 *
 * All methods are STATIC because:
 *   - There should only be one timer monitor for the whole application
 *   - Callers don't need to create an instance - they just call the methods directly
 *   - This matches the "utility class" pattern (like java.lang.Math)
 */
public class TableTimerMonitor {

    // =========================================================================
    // CONFIGURATION CONSTANT
    // =========================================================================

    /**
     * Maximum number of minutes a table can be occupied before an alert is raised.
     * Change this value to adjust the alert threshold.
     * 'static final' makes it a compile-time constant (like a #define in C/C++).
     */
    private static final long MAX_DINING_MINUTES = 60;

    // =========================================================================
    // SHARED DATA STRUCTURES (static = shared across all callers)
    // =========================================================================

    /**
     * Tracks when each table became occupied.
     * Key   = Table ID (e.g., 3 for "Table 3")
     * Value = The LocalDateTime when the table was seated
     *
     * ConcurrentHashMap is used because multiple threads access this map:
     *   - The UI thread adds/removes entries via tableOccupied()/tableFreed()
     *   - The scheduler thread reads all entries in checkTables()
     */
    private static final ConcurrentHashMap<Integer, LocalDateTime> occupiedSince = new ConcurrentHashMap<>();

    /**
     * The scheduled executor that runs checkTables() every 2 minutes.
     * Stored as a field so we could potentially call executor.shutdown() later.
     */
    private static ScheduledExecutorService executor;

    // =========================================================================
    // startMonitoring() - Launch the background monitoring service
    // =========================================================================

    /**
     * Starts the background daemon monitoring service.
     *
     * Call this ONCE when the application starts (e.g., in MainApp.main()).
     * It creates a single-threaded ScheduledExecutorService configured as
     * a daemon thread, then schedules checkTables() to run every 2 minutes.
     *
     * SAFE TO CALL MULTIPLE TIMES: If the executor already exists and isn't
     * shut down, we skip re-creating it to avoid duplicate background threads.
     */
    public static void startMonitoring() {
        // Guard: don't start a second executor if one already exists
        if (executor != null && !executor.isShutdown()) {
            System.out.println("[TableTimerMonitor] Monitoring is already running.");
            return;
        }

        // Create a custom ThreadFactory to make our thread a DAEMON thread
        // A ThreadFactory is a functional interface (1 method) that creates Thread objects.
        // We use a lambda expression here (Java 8+) for conciseness.
        ThreadFactory daemonThreadFactory = runnable -> {
            Thread thread = new Thread(runnable);

            // *** CRITICAL: Mark this thread as a DAEMON thread ***
            // This means the JVM can exit even if this thread is still sleeping
            // between check intervals. Without this, the app would never close!
            thread.setDaemon(true);

            thread.setName("TableTimerMonitor-Thread"); // Give it a descriptive name for debugging
            return thread;
        };

        // Create a ScheduledExecutorService with 1 thread, using our daemon factory
        // newSingleThreadScheduledExecutor() = one dedicated background thread
        executor = Executors.newSingleThreadScheduledExecutor(daemonThreadFactory);

        // Schedule checkTables() to run:
        //   - Initial delay: 60 seconds (don't check immediately on startup)
        //   - Period: 120 seconds (run every 2 minutes after that)
        //   - Unit: TimeUnit.SECONDS
        executor.scheduleAtFixedRate(
                TableTimerMonitor::checkTables, // Method reference = run checkTables() each time
                60,                             // Wait 60 seconds before first check
                120,                            // Then repeat every 120 seconds (2 minutes)
                TimeUnit.SECONDS                // The unit for the numbers above
        );

        System.out.println("[TableTimerMonitor] Background monitoring started. " +
                           "Checking every 2 minutes for tables occupied > " + MAX_DINING_MINUTES + " minutes.");
    }

    // =========================================================================
    // tableOccupied() - Register that a table has just been seated
    // =========================================================================

    /**
     * Records the current time as the occupancy start time for a table.
     *
     * Call this method whenever a customer is seated at a table.
     * The ConcurrentHashMap stores the mapping: tableId → now
     *
     * @param tableId The ID of the table that just became occupied
     */
    public static void tableOccupied(int tableId) {
        // LocalDateTime.now() captures the current date and time
        // ConcurrentHashMap.put() is thread-safe - safe to call from any thread
        occupiedSince.put(tableId, LocalDateTime.now());
        System.out.println("[TableTimerMonitor] Table " + tableId + " is now being tracked (occupied at " +
                           LocalDateTime.now() + ").");
    }

    // =========================================================================
    // tableFreed() - Unregister a table when it becomes available
    // =========================================================================

    /**
     * Removes a table from the monitoring map when it is freed.
     *
     * Call this method when a table's bill is paid and the table is cleared.
     * If we don't remove it, we'll keep getting false alerts for a free table!
     *
     * @param tableId The ID of the table that is now free
     */
    public static void tableFreed(int tableId) {
        // ConcurrentHashMap.remove() safely removes the key-value pair
        LocalDateTime startTime = occupiedSince.remove(tableId);

        if (startTime != null) {
            long minutesOccupied = ChronoUnit.MINUTES.between(startTime, LocalDateTime.now());
            System.out.println("[TableTimerMonitor] Table " + tableId + " freed after " +
                               minutesOccupied + " minutes.");
        } else {
            System.out.println("[TableTimerMonitor] Table " + tableId + " was not being tracked.");
        }
    }

    // =========================================================================
    // checkTables() - The periodic check (runs every 2 minutes)
    // =========================================================================

    /**
     * Iterates over all currently occupied tables and checks if any have
     * exceeded MAX_DINING_MINUTES. Prints an alert for tables that have.
     *
     * This method is called automatically by the ScheduledExecutorService
     * every 2 minutes. You should NOT call it directly.
     *
     * We use 'synchronized(occupiedSince)' as an extra safety measure:
     * While ConcurrentHashMap handles individual operations (put, get, remove)
     * safely, iterating over the full entry set while another thread is
     * modifying the map can still cause issues. The synchronized block
     * ensures the WHOLE iteration is treated as one atomic operation.
     */
    private static void checkTables() {
        System.out.println("[TableTimerMonitor] Running periodic table occupancy check...");

        // Check if there's anything to monitor
        if (occupiedSince.isEmpty()) {
            System.out.println("[TableTimerMonitor] No tables currently being tracked.");
            return;
        }

        // The 'synchronized' keyword here locks the occupiedSince object.
        // Any other thread that tries to enter a 'synchronized(occupiedSince)' block
        // will WAIT until this block finishes.
        // This prevents the map from being modified (table freed/occupied) while
        // we're in the middle of iterating over all its entries.
        synchronized (occupiedSince) {

            // Get the current time ONCE before the loop (more efficient than calling now() repeatedly)
            LocalDateTime now = LocalDateTime.now();

            // Iterate over every entry (tableId → occupancyStartTime) in the map
            // Map.Entry represents a single key-value pair
            for (Map.Entry<Integer, LocalDateTime> entry : occupiedSince.entrySet()) {

                int tableId = entry.getKey();            // The table number (e.g., 3)
                LocalDateTime occupancyStart = entry.getValue(); // When it was seated

                // Calculate how many WHOLE minutes have passed since the table was seated
                // ChronoUnit.MINUTES.between(start, end) counts minutes between two times
                long minutesOccupied = ChronoUnit.MINUTES.between(occupancyStart, now);

                // Check if this table has exceeded the maximum allowed dining time
                if (minutesOccupied > MAX_DINING_MINUTES) {
                    // Print the alert in the specified format
                    // The ⚠ symbol is a Unicode warning sign for visual emphasis
                    System.out.println("⚠ [TABLE ALERT] Table " + tableId +
                                       " has been occupied for " + minutesOccupied +
                                       " minutes. Please check!");
                }
            }
        } // The synchronized block ends here - other threads can now access occupiedSince

        System.out.println("[TableTimerMonitor] Periodic check complete.");
    }
}
