package com.pixelforge.restaurant.service.analytics;

import com.pixelforge.restaurant.model.Bill;
import com.pixelforge.restaurant.model.Order;
import com.pixelforge.restaurant.model.OrderItem;
import com.pixelforge.restaurant.dao.BillDAO;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * =============================================================================
 *  BillingAndAnalyticsService.java
 *  Author : Member 2 — Gurnaaz
 *  Purpose: Handles all billing calculations and analytics for the PixelForge
 *           Restaurant Billing System.
 *
 *  Java Features showcased in this file:
 *  ─────────────────────────────────────
 *  ✔ Lambda Expressions     — anonymous function syntax (item -> ...)
 *  ✔ Method References      — shorthand lambdas (OrderItem::getItemTotal)
 *  ✔ Stream API             — filter, map, mapToDouble, reduce, collect
 *  ✔ Collectors.groupingBy  — group stream elements into a Map
 *  ✔ Collectors.summingDouble / summingInt — aggregate within groups
 *  ✔ Optional<T>            — safe container that avoids NullPointerException
 *  ✔ java.time.LocalDateTime — modern date/time API (replaces legacy Date)
 *  ✔ DateTimeFormatter      — format LocalDateTime into human-readable strings
 * =============================================================================
 */
public class BillingAndAnalyticsService {

    // -------------------------------------------------------------------------
    // The BillDAO handles all database reads/writes for bills.
    // We receive it through the constructor (Dependency Injection pattern),
    // which makes the service easy to test and loosely coupled.
    // -------------------------------------------------------------------------
    private final BillDAO billDAO;

    /**
     * Constructor — receives a BillDAO so this service can query the database.
     *
     * @param billDAO data-access object for the bills table
     */
    public BillingAndAnalyticsService(BillDAO billDAO) {
        this.billDAO = billDAO;
    }

    // =========================================================================
    //  BILLING METHODS
    // =========================================================================

    /**
     * Calculates the subtotal of an order by summing each item's total price.
     *
     * <p><b>Stream API — replaces manual for-loop summation</b></p>
     * <pre>
     *   Traditional for-loop:
     *     double sum = 0;
     *     for (OrderItem item : items) { sum += item.getItemTotal(); }
     *
     *   Stream equivalent (below):
     *     items.stream()                      // create a Stream<OrderItem>
     *          .mapToDouble(...)              // convert each item to a double
     *          .sum()                         // terminal operation: add them up
     * </pre>
     *
     * <p>
     * {@code OrderItem::getItemTotal} is a <em>method reference</em> — a
     * compact form of the lambda {@code item -> item.getItemTotal()}.
     * It is an instance of the functional interface {@code ToDoubleFunction<OrderItem>}.
     * </p>
     *
     * @param items list of ordered items for one table's order
     * @return sum of (price × quantity) for every item
     */
    public double calculateSubtotal(List<OrderItem> items) {
        // stream()       → open a pipeline on the list
        // mapToDouble()  → maps each OrderItem to a primitive double using
        //                   the method reference OrderItem::getItemTotal
        // sum()          → terminal operation; collapses the stream to one double
        return items.stream()
                    .mapToDouble(OrderItem::getItemTotal)
                    .sum();
    }

    /**
     * Calculates GST (Goods and Services Tax) on the subtotal.
     *
     * <p><b>5% GST as per Indian tax structure</b> — currently a flat 5 % rate
     * applies to restaurant services under India's GST regime.</p>
     *
     * @param subtotal pre-tax order amount
     * @return GST amount (5 % of subtotal)
     */
    public double calculateGST(double subtotal) {
        // 5 % GST — simple multiplication; kept as a dedicated method so the
        // tax rate can be changed in one place without touching every caller.
        return subtotal * 0.05;
    }

    /**
     * Adds GST to the subtotal to produce the final amount a customer pays.
     *
     * @param subtotal pre-tax amount
     * @param gst      tax amount (from {@link #calculateGST(double)})
     * @return grand total (subtotal + GST)
     */
    public double calculateGrandTotal(double subtotal, double gst) {
        return subtotal + gst;
    }

    /**
     * Generates a fully populated {@link Bill} object for the given order.
     *
     * <p><b>java.time.LocalDateTime</b> — Java 8 introduced the {@code java.time}
     * package as a replacement for the error-prone {@code java.util.Date}.
     * {@code LocalDateTime.now()} captures the exact moment the bill is printed,
     * including date and time down to nanoseconds, without time-zone complexity.
     * This timestamp is stored in the database alongside the bill so management
     * can run time-based reports later.</p>
     *
     * @param order              the order whose items will be billed
     * @param formattedBillDetails a pre-formatted string of line items (passed
     *                            from the UI / controller layer)
     * @return a {@link Bill} ready to be persisted via {@link BillDAO}
     */
    public Bill generateBill(Order order, String formattedBillDetails) {
        // Compute each financial field in sequence
        double subtotal   = calculateSubtotal(order.getItems());
        double gst        = calculateGST(subtotal);
        double grandTotal = calculateGrandTotal(subtotal, gst);

        // LocalDateTime.now() — captures the wall-clock date and time at this
        // exact instant on the server.  Much cleaner than new Date() / Calendar.
        LocalDateTime billTime = LocalDateTime.now();

        // Construct and return the Bill.  The Bill constructor is expected to
        // accept (tableId, billDetails, subtotal, gst, grandTotal, billTime).
        return new Bill(
                order.getTableId(),
                formattedBillDetails,
                subtotal,
                gst,
                grandTotal,
                billTime
        );
    }

    // =========================================================================
    //  ANALYTICS METHODS
    // =========================================================================

    /**
     * (a) Returns the total revenue across ALL bills stored in the database.
     *
     * <p><b>Stream + reduce()</b> — {@code reduce} is a <em>fold</em> operation:
     * it starts with an identity value (0.0) and applies an accumulator
     * function repeatedly until the stream is exhausted.</p>
     *
     * <pre>
     *   reduce(identity, accumulator)
     *     identity    = 0.0           (starting value)
     *     accumulator = (sum, bill) -> sum + bill.getGrandTotal()
     *                               (lambda: add each bill's total to running sum)
     * </pre>
     *
     * <p>An alternative would be {@code mapToDouble(...).sum()}, but
     * {@code reduce} is shown here to demonstrate the general fold pattern.</p>
     *
     * @return total revenue (sum of grand_total from every bill row)
     */
    public double getDailyRevenue() {
        try {
            List<Bill> allBills = billDAO.getAllBills();

            // stream()     → pipeline over the bill list
            // map(...)     → transform each Bill into its grand total (a Double)
            // reduce(...)  → fold all doubles into a single sum, starting at 0.0
            //
            // The lambda   (runningSum, currentTotal) -> runningSum + currentTotal
            // is an instance of the functional interface BinaryOperator<Double>.
            return allBills.stream()
                           .map(Bill::getGrandTotal)        // Stream<Double>
                           .reduce(0.0, Double::sum);       // method ref for (a,b)->a+b
        } catch (SQLException e) {
            System.err.println("[BillingAndAnalyticsService] Error fetching bills: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * (b) Returns the total number of bills ever generated (from the database).
     *
     * @return count of bill records in the bills table
     */
    public int getTotalBillsGenerated() {
        try {
            // getAllBills() fetches the full list; .size() gives the count.
            // No Stream needed here — simple delegation to the DAO.
            return billDAO.getAllBills().size();
        } catch (SQLException e) {
            System.err.println("[BillingAndAnalyticsService] Error fetching bill count: " + e.getMessage());
            return 0;
        }
    }

    /**
     * (c) Returns a breakdown of revenue grouped by menu-item category,
     *     computed from the current session's order items.
     *
     * <p><b>Collectors.groupingBy</b> — partitions stream elements into groups
     * sharing the same key.  The downstream collector {@code summingDouble}
     * aggregates every element in a group into a single double.</p>
     *
     * <pre>
     *   groupingBy(
     *     classifier  : item -> item.getMenuItem().getCategory()   // key function
     *     downstream  : Collectors.summingDouble(OrderItem::getItemTotal) // aggregator
     *   )
     *   Result type → Map&lt;String, Double&gt;
     *                   key   = category name  (e.g. "Starters", "Main Course")
     *                   value = total revenue for that category
     * </pre>
     *
     * <p>The classifier lambda is an instance of the functional interface
     * {@code Function<OrderItem, String>} — it maps an input to an output.</p>
     *
     * @param allSessionItems every OrderItem placed during the session
     * @return map of category → total revenue
     */
    public Map<String, Double> getCategoryWiseRevenue(List<OrderItem> allSessionItems) {
        // collect()        → terminal operation; gathers stream results into a container
        // groupingBy(...)  → groups items by their menu-item category
        // summingDouble()  → within each group, sums the item totals (price × qty)
        return allSessionItems.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getMenuItem().getCategory(),   // classifier lambda
                        Collectors.summingDouble(OrderItem::getItemTotal) // downstream
                ));
    }

    /**
     * (d) Finds the top-selling dish in the current session by total quantity sold.
     *
     * <p><b>Optional&lt;T&gt;</b> — a container that may or may not hold a value.
     * Instead of returning {@code null} when no items exist (which would force
     * every caller to add a null-check or risk a {@link NullPointerException}),
     * we return {@code Optional.ofNullable(result)}.  The caller can then use
     * {@code ifPresent()} or {@code orElse()} to handle the absent case safely.</p>
     *
     * <p>Algorithm:</p>
     * <ol>
     *   <li>Group all items by dish name, summing quantities → {@code Map<String, Integer>}</li>
     *   <li>Stream the map's entries and find the entry with the maximum quantity.</li>
     *   <li>Wrap the winning OrderItem (if found) in an {@code Optional}.</li>
     * </ol>
     *
     * @param allSessionItems every OrderItem placed during the session
     * @return an Optional containing the top-selling OrderItem, or empty if no items
     */
    public Optional<OrderItem> getTopSellingItem(List<OrderItem> allSessionItems) {
        if (allSessionItems == null || allSessionItems.isEmpty()) {
            // Optional.empty() — explicitly signals "no result" without returning null
            return Optional.empty();
        }

        // Step 1: Group items by dish name, accumulating total quantity per dish.
        //
        // groupingBy(classifier, downstream)
        //   classifier : item -> item.getMenuItem().getName()   → the dish name key
        //   downstream : Collectors.summingInt(OrderItem::getQuantity) → sum quantities
        //
        // Result: Map<String, Integer>  e.g. {"Butter Chicken" -> 5, "Naan" -> 8}
        Map<String, Integer> quantityByName = allSessionItems.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getMenuItem().getName(),         // Function<OrderItem,String>
                        Collectors.summingInt(OrderItem::getQuantity) // ToIntFunction<OrderItem>
                ));

        // Step 2: Find the dish name with the highest total quantity.
        //
        // entrySet().stream()  → Stream<Map.Entry<String, Integer>>
        // max(Comparator)      → terminal op; finds the entry with the largest value
        //                        Returns Optional<Map.Entry<String,Integer>>
        // Map.Entry::getValue  → method reference; comparingByValue
        Optional<Map.Entry<String, Integer>> topEntry =
                quantityByName.entrySet().stream()
                              .max(Comparator.comparingInt(Map.Entry::getValue));

        // Step 3: If a winning entry exists, find a matching OrderItem by name
        //         and return it wrapped in Optional.  Otherwise return empty.
        //
        // Optional.ifPresent() — the lambda inside runs ONLY if a value is present;
        // this avoids a manual null-check completely. Here we use flatMap to chain
        // two Optional operations cleanly.
        if (topEntry.isPresent()) {
            String topName = topEntry.get().getKey();

            // filter() on stream — a Predicate<OrderItem> that keeps only the
            // item whose name matches the top-selling dish
            // findFirst() — terminal op returning Optional<OrderItem>
            return allSessionItems.stream()
                    .filter(item -> item.getMenuItem().getName().equals(topName))
                    .findFirst(); // already returns Optional<OrderItem>
        }

        return Optional.empty();
    }

    /**
     * (e) Prints a formatted daily sales report to the console.
     *
     * <p>This method ties together every other method in this service,
     * demonstrating how streams, groupingBy, and Optional cooperate to build
     * a readable analytics dashboard without any explicit null-checks or loops.</p>
     *
     * <p><b>DateTimeFormatter</b> — patterns follow {@code java.time} conventions:
     * {@code dd-MM-yyyy} → "20-11-2026",  {@code HH:mm:ss} → "14:03:12".</p>
     *
     * @param allSessionItems every OrderItem placed across all tables today
     */
    public void printDailySalesReport(List<OrderItem> allSessionItems) {
        // ── Header ──────────────────────────────────────────────────────────
        // DateTimeFormatter.ofPattern() creates a formatter from a pattern string.
        // LocalDateTime.now().format(formatter) converts the current moment
        // into a human-readable string (e.g. "20-11-2026 14:03:12").
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        String reportDateTime = LocalDateTime.now().format(dtf);

        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║      PIXELFORGE RESTAURANT — DAILY SALES REPORT  ║");
        System.out.println("║      Generated : " + reportDateTime + "       ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        // ── (a) Total Revenue ───────────────────────────────────────────────
        // getDailyRevenue() calls billDAO.getAllBills() and uses Stream + reduce
        double totalRevenue = getDailyRevenue();
        System.out.printf("  %-30s ₹ %10.2f%n", "Total Revenue (from DB):", totalRevenue);

        // ── (b) Total Bills ─────────────────────────────────────────────────
        int totalBills = getTotalBillsGenerated();
        System.out.printf("  %-30s %10d%n", "Total Bills Generated:", totalBills);
        System.out.println();

        // ── (c) Category-wise Revenue ────────────────────────────────────────
        System.out.println("  ── Revenue by Category ───────────────────────────");
        Map<String, Double> categoryRevenue = getCategoryWiseRevenue(allSessionItems);

        if (categoryRevenue.isEmpty()) {
            System.out.println("  (No session orders to analyse)");
        } else {
            // entrySet().stream() — iterate map entries as a stream
            // sorted() — uses a Comparator to order by revenue descending
            // forEach() — terminal operation; executes a Consumer for each entry
            //
            // The lambda (entry -> System.out.printf(...)) is a Consumer<Map.Entry<String,Double>>
            categoryRevenue.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                    .forEach(entry ->
                            System.out.printf("    %-20s ₹ %10.2f%n",
                                    entry.getKey(), entry.getValue())
                    );
        }
        System.out.println();

        // ── (d) Top-Selling Dish ─────────────────────────────────────────────
        System.out.println("  ── Top Selling Dish ──────────────────────────────");

        // getTopSellingItem() returns Optional<OrderItem>.
        //
        // Optional.ifPresent(Consumer) — the lambda runs ONLY when a value exists.
        // This is the key benefit of Optional: NO manual null-check required.
        // If the list was empty, we simply fall through to the else block.
        Optional<OrderItem> topItem = getTopSellingItem(allSessionItems);

        topItem.ifPresentOrElse(
                item -> {
                    // This lambda (Consumer<OrderItem>) is called only if Optional has a value
                    System.out.printf("    %-20s  (qty: %d)%n",
                            item.getMenuItem().getName(),
                            item.getQuantity());
                },
                () -> System.out.println("    No items ordered in this session.")
        );

        System.out.println();
        System.out.println("══════════════════════════════════════════════════════");
        System.out.println("  End of Report");
        System.out.println("══════════════════════════════════════════════════════");
    }
}
