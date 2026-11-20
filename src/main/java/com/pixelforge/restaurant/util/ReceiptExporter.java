package com.pixelforge.restaurant.util;

import com.pixelforge.restaurant.model.Bill;
import com.pixelforge.restaurant.model.MenuItem;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================
 * ReceiptExporter - File I/O Utility Class
 * ============================================================
 *
 * PURPOSE:
 * --------
 * Handles all file input/output operations for the restaurant system:
 *   1. Export bills as formatted .txt receipt files
 *   2. Export the menu to a .csv backup file
 *   3. Import menu items from a .csv file
 *
 * NO DATABASE CODE IN THIS CLASS.
 * This is a pure file I/O utility — separation of concerns.
 *
 * KEY JAVA CONCEPTS USED:
 * -----------------------
 * 1. BufferedWriter  → Writes text to files efficiently (with internal buffer)
 * 2. BufferedReader  → Reads text from files efficiently (line by line)
 * 3. FileWriter      → Underlying character stream that writes to a File
 * 4. FileReader      → Underlying character stream that reads from a File
 * 5. StringBuilder   → Efficient string building in loops (see note below)
 * 6. try-with-resources → Auto-closes streams (see note below)
 * 7. File.mkdirs()   → Creates directory tree if it doesn't exist
 *
 * ---------------------------------------------------------------
 * WHY StringBuilder INSTEAD OF String CONCATENATION IN LOOPS?
 * ---------------------------------------------------------------
 * String is IMMUTABLE in Java. Each "str = str + part" creates a NEW
 * String object in memory, leaving the old one for GC. In a loop over
 * 100 items, this creates 100 temporary String objects → memory waste.
 *
 * StringBuilder is MUTABLE — it uses a resizable char array internally.
 * sb.append(part) modifies the SAME object — no new allocation per step.
 *
 * Example:
 *   SLOW (new String object per iteration):
 *     String s = "";
 *     for (MenuItem item : items) s += item.getName() + "\n";   // BAD in loops
 *
 *   FAST (single object, in-place modification):
 *     StringBuilder sb = new StringBuilder();
 *     for (MenuItem item : items) sb.append(item.getName()).append("\n"); // GOOD
 *
 * ---------------------------------------------------------------
 * WHY try-with-resources INSTEAD OF traditional try-finally?
 * ---------------------------------------------------------------
 * Traditional try-finally requires manual stream closing:
 *   BufferedWriter w = null;
 *   try {
 *       w = new BufferedWriter(new FileWriter(file));
 *       w.write("data");
 *   } catch (IOException e) {
 *       e.printStackTrace();
 *   } finally {
 *       if (w != null) {          // must null-check
 *           try { w.close(); }    // close can ALSO throw IOException!
 *           catch (IOException e) { e.printStackTrace(); }
 *       }
 *   }
 *
 * try-with-resources (Java 7+) handles this automatically:
 *   try (BufferedWriter w = new BufferedWriter(new FileWriter(file))) {
 *       w.write("data");
 *   }   // ← w.close() is called AUTOMATICALLY here, even if exception occurs
 *       // The resource must implement java.lang.AutoCloseable
 *
 * Benefits:
 *   ✓ No manual null checks
 *   ✓ Stream ALWAYS closed (no resource leaks)
 *   ✓ Cleaner, shorter code
 *   ✓ Handles close() exceptions via suppressed exceptions
 * ============================================================
 */
public class ReceiptExporter {

    /** Directory where all receipt .txt files and CSV backups are stored */
    private static final String RECEIPTS_DIR = "receipts/";

    /** CSV header for menu export */
    private static final String CSV_HEADER = "ItemID,Name,Category,Price";

    // ================================================================
    // METHOD 1: Export a Bill as a formatted .txt receipt
    // ================================================================

    /**
     * Exports a Bill object to a human-readable .txt receipt file,
     * automatically extracting the tableId from the bill.
     *
     * @param bill The Bill object containing order details and totals
     */
    public static void exportBillToFile(Bill bill) {
        if (bill != null) {
            exportBillToFile(bill, bill.getTableId());
        }
    }

    /**
     * Exports a Bill object to a human-readable .txt receipt file.
     *
     * File path: receipts/Bill_Table_<tableId>_<billId>.txt
     *
     * Uses try-with-resources to ensure the BufferedWriter is closed
     * automatically after writing, regardless of success or failure.
     *
     * @param bill   The Bill object containing order details and totals
     * @param tableId The table number (used in the filename)
     */
    public static void exportBillToFile(Bill bill, int tableId) {
        // Step 1: Create the receipts/ directory if it doesn't already exist
        File dir = new File(RECEIPTS_DIR);
        if (!dir.exists()) {
            dir.mkdirs(); // Creates receipts/ and any missing parent directories
        }

        // Step 2: Construct a unique filename using tableId and billId
        String fileName = RECEIPTS_DIR + "Bill_Table_" + tableId + "_Bill_" + bill.getBillId() + ".txt";
        File file = new File(fileName);

        // Step 3: Build the receipt content using StringBuilder (efficient string assembly)
        StringBuilder receiptContent = new StringBuilder();
        receiptContent.append("╔══════════════════════════════════════════════╗\n");
        receiptContent.append("║          PIXELFORGE RESTAURANT               ║\n");
        receiptContent.append("║         ~ Fine Dining & Hospitality ~        ║\n");
        receiptContent.append("╚══════════════════════════════════════════════╝\n");
        receiptContent.append("\n");
        receiptContent.append("Table No    : ").append(tableId).append("\n");
        receiptContent.append("Bill ID     : ").append(bill.getBillId()).append("\n");
        receiptContent.append("Bill Date   : ").append(bill.getBillTime()).append("\n");
        receiptContent.append("------------------------------------------------\n");
        receiptContent.append(bill.getBillDetails()); // Order line items from Bill object
        receiptContent.append("------------------------------------------------\n");
        receiptContent.append(String.format("%-30s : ₹ %8.2f%n", "Subtotal", bill.getSubtotal()));
        receiptContent.append(String.format("%-30s : ₹ %8.2f%n", "GST (5%)", bill.getGst()));
        receiptContent.append("================================================\n");
        receiptContent.append(String.format("%-30s : ₹ %8.2f%n", "GRAND TOTAL", bill.getGrandTotal()));
        receiptContent.append("================================================\n");
        receiptContent.append("\n   Thank you for dining with us! Visit again!\n");
        receiptContent.append("          ~ PixelForge Restaurant ~\n");

        // Step 4: Write to file using try-with-resources
        // The BufferedWriter is declared inside try(...) → it implements AutoCloseable
        // → Java calls writer.close() automatically when the try block exits (normally or via exception)
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(receiptContent.toString());
            // No need to call writer.close() — try-with-resources handles it!
            System.out.println("✔ Receipt saved to: " + fileName);
        } catch (IOException e) {
            // If file creation or writing fails (e.g., permission denied, disk full)
            System.err.println("✘ Failed to export receipt: " + e.getMessage());
        }
        // After this closing brace, writer.close() has already been called automatically
    }

    // ================================================================
    // METHOD 2: Export the Menu as a CSV file
    // ================================================================

    /**
     * Exports all menu items to a CSV file at: receipts/menu_backup.csv
     *
     * CSV format:
     *   ItemID,Name,Category,Price
     *   1,Paneer Tikka,Starters,220.00
     *   2,Butter Chicken,Main Course,350.00
     *   ...
     *
     * Uses try-with-resources with BufferedWriter for efficient writing.
     *
     * @param items List of MenuItem objects to export
     */
    public static void exportMenuToCSV(List<MenuItem> items) {
        // Ensure the receipts/ directory exists
        File dir = new File(RECEIPTS_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String fileName = RECEIPTS_DIR + "menu_backup.csv";
        File file = new File(fileName);

        // try-with-resources: BufferedWriter auto-closed after the block
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {

            // Write CSV header first
            writer.write(CSV_HEADER);
            writer.newLine(); // Platform-independent newline (\n on Unix, \r\n on Windows)

            // Use StringBuilder inside loop for efficient line building
            // (avoids creating a new String object for each item)
            StringBuilder lineBuilder = new StringBuilder();

            for (MenuItem item : items) {
                lineBuilder.setLength(0); // Reset/clear the builder for reuse (no new object!)
                lineBuilder.append(item.getItemId())
                           .append(",")
                           .append(escapeCsv(item.getName()))          // Escape commas in names
                           .append(",")
                           .append(escapeCsv(item.getCategory()))
                           .append(",")
                           .append(String.format("%.2f", item.getPrice())); // 2 decimal places

                writer.write(lineBuilder.toString());
                writer.newLine();
            }

            System.out.println("✔ Menu exported to CSV: " + fileName + " (" + items.size() + " items)");

        } catch (IOException e) {
            System.err.println("✘ Failed to export menu CSV: " + e.getMessage());
        }
    }

    // ================================================================
    // METHOD 3: Import Menu Items from a CSV file
    // ================================================================

    /**
     * Reads menu items from a CSV file and returns them as a List<MenuItem>.
     *
     * Expected CSV format:
     *   ItemID,Name,Category,Price   ← header (skipped)
     *   1,Paneer Tikka,Starters,220.00
     *   2,Butter Chicken,Main Course,350.00
     *
     * Uses try-with-resources with BufferedReader for line-by-line reading.
     * Handles malformed lines gracefully with per-line error handling.
     *
     * @param filePath Absolute or relative path to the CSV file
     * @return List of MenuItem objects parsed from the file (empty list if file not found)
     */
    public static List<MenuItem> importMenuFromCSV(String filePath) {
        List<MenuItem> importedItems = new ArrayList<>();
        File file = new File(filePath);

        // Guard: check file exists before opening
        if (!file.exists()) {
            System.err.println("✘ File not found: " + filePath);
            return importedItems; // Return empty list — caller decides what to do
        }

        // try-with-resources: BufferedReader auto-closed after block
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {

            String line;
            boolean isFirstLine = true; // Flag to skip the header row

            while ((line = reader.readLine()) != null) {
                // Skip the header row (ItemID,Name,Category,Price)
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Jump to next iteration without processing header
                }

                // Skip blank lines
                if (line.trim().isEmpty()) {
                    continue;
                }

                // Parse individual line — handle per-line errors gracefully
                // so one bad line doesn't abort the entire import
                try {
                    MenuItem item = parseCSVLine(line);
                    if (item != null) {
                        importedItems.add(item);
                    }
                } catch (NumberFormatException e) {
                    // If itemId or price is malformed (e.g., "abc" instead of a number)
                    // Log a warning and continue to next line
                    System.err.println("⚠ Skipping malformed line (number parse error): [" + line + "]");
                } catch (ArrayIndexOutOfBoundsException e) {
                    // If the line doesn't have enough comma-separated fields
                    System.err.println("⚠ Skipping malformed line (missing fields): [" + line + "]");
                }
            }

            System.out.println("✔ Imported " + importedItems.size() + " menu items from: " + filePath);

        } catch (IOException e) {
            System.err.println("✘ Error reading file: " + e.getMessage());
        }

        return importedItems;
    }

    // ================================================================
    // PRIVATE HELPER METHODS
    // ================================================================

    /**
     * Parses a single CSV line into a MenuItem object.
     * Format: ItemID,Name,Category,Price
     *
     * @param line A single CSV data line (not the header)
     * @return Parsed MenuItem, or null if the line is invalid
     * @throws NumberFormatException if itemId or price cannot be parsed
     */
    private static MenuItem parseCSVLine(String line) {
        // Split on comma — limit 4 to handle commas inside quoted fields
        String[] parts = line.split(",", 4);

        if (parts.length < 4) {
            System.err.println("⚠ Line has fewer than 4 fields, skipping: [" + line + "]");
            return null;
        }

        int itemId       = Integer.parseInt(parts[0].trim());        // throws NumberFormatException if bad
        String name      = unescapeCsv(parts[1].trim());
        String category  = unescapeCsv(parts[2].trim());
        double price     = Double.parseDouble(parts[3].trim());      // throws NumberFormatException if bad

        // Validate parsed values
        if (name.isEmpty() || category.isEmpty() || price <= 0) {
            System.err.println("⚠ Invalid data in line, skipping: [" + line + "]");
            return null;
        }

        // Create and return a MenuItem populated from the CSV line
        return new MenuItem(itemId, name, category, price);
    }

    /**
     * Wraps a CSV field value in double quotes if it contains a comma.
     * This prevents comma-in-data from corrupting the CSV column structure.
     *
     * @param value The raw field value
     * @return Quoted value if it contains a comma, otherwise original value
     */
    private static String escapeCsv(String value) {
        if (value == null) return "";
        // If the value has a comma, wrap it in double quotes
        if (value.contains(",")) {
            return "\"" + value.replace("\"", "\"\"") + "\""; // Also escape any existing quotes
        }
        return value;
    }

    /**
     * Removes surrounding double quotes from a CSV field value if present.
     *
     * @param value The raw CSV field (possibly quoted)
     * @return Unquoted field value
     */
    private static String unescapeCsv(String value) {
        if (value != null && value.startsWith("\"") && value.endsWith("\"")) {
            // Remove surrounding quotes and un-escape doubled quotes ("")  → "
            return value.substring(1, value.length() - 1).replace("\"\"", "\"");
        }
        return value;
    }
}
