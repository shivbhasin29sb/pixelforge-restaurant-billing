package com.pixelforge.restaurant.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ============================================================
 * UTILITY CLASS: CacheStore<T>  (Generic In-Memory Cache)
 * ============================================================
 *
 * WHAT IS A GENERIC CLASS?
 * -----------------------------------------
 * In Java, a "generic" class is one that works with ANY type
 * rather than being tied to a specific one.
 *
 * The <T> in "CacheStore<T>" is a TYPE PARAMETER — a placeholder
 * for whatever type the caller decides to use.
 *
 * Example usages:
 *   CacheStore<Order>            → stores Order objects by tableId
 *   CacheStore<MenuItem>         → stores MenuItem objects by itemId
 *   CacheStore<RestaurantTable>  → stores tables by tableId
 *
 * This avoids writing three separate cache classes for each type.
 * Instead, one generic class handles them all!
 *
 * WHY USE A CACHE?
 * -----------------------------------------
 * Database reads are SLOW (network + disk I/O).
 * Fetching data for every small operation would be very costly.
 * Instead, we load data into RAM (memory) ONCE at startup, and
 * then do all lookups from this fast in-memory map.
 *
 * This pattern is called a "Cache" or "In-Memory Store".
 * It speeds up reads significantly but means memory data may be
 * slightly out of sync with the DB if not managed carefully.
 *
 * WHY HashMap<Integer, T>?
 * -----------------------------------------
 * HashMap provides O(1) average-time lookup.
 * That means no matter how many items are in the map, looking
 * one up by its integer ID takes roughly the same (constant) time.
 * Compare that to ArrayList, where you'd have to loop through
 * every element (O(n)) to find the right one.
 *
 * Keys   = Integer (the item's ID, e.g., tableId, itemId)
 * Values = T       (the actual object stored, e.g., an Order)
 * ============================================================
 */
public class CacheStore<T> {

    /**
     * The underlying data structure.
     * HashMap<Integer, T>:
     *   Integer = the lookup key (ID)
     *   T       = the value object (generic — can be any type)
     *
     * 'private' ensures only this class can directly touch 'store'.
     * All interaction happens through our controlled public methods below.
     */
    private Map<Integer, T> store = new HashMap<>();

    // ============================================================
    // METHODS
    // ============================================================

    /**
     * Stores an item in the cache under a given integer key.
     * If an item with the same key already exists, it is replaced.
     *
     * Time complexity: O(1) average — HashMap puts in constant time.
     *
     * @param id   The unique integer key (e.g., tableId, menuItemId)
     * @param item The object to store (must not be null ideally)
     */
    public void put(int id, T item) {
        store.put(id, item);
    }

    /**
     * Retrieves an item from the cache by its key.
     * Returns null if no item exists for the given key.
     *
     * IMPORTANT: Callers must check for null before using the result!
     *   Order o = activeOrders.get(5);
     *   if (o != null) { ... }
     *
     * Time complexity: O(1) average — HashMap lookup is very fast.
     *
     * @param id The key to look up
     * @return   The stored item, or null if not found
     */
    public T get(int id) {
        // HashMap.get() returns null automatically if the key doesn't exist.
        return store.get(id);
    }

    /**
     * Removes an item from the cache by its key.
     * If the key doesn't exist, nothing happens (no error).
     *
     * Time complexity: O(1) average.
     *
     * @param id The key of the item to remove
     */
    public void remove(int id) {
        store.remove(id);
    }

    /**
     * Checks whether an item with the given key exists in the cache.
     *
     * Time complexity: O(1) average.
     *
     * @param id The key to check for
     * @return   true if the key exists, false otherwise
     */
    public boolean contains(int id) {
        return store.containsKey(id);
    }

    /**
     * Returns ALL items currently stored in the cache as a List.
     *
     * We return a NEW ArrayList (a copy of the values) rather than
     * the raw map values. This protects the internal store from
     * accidental external modification (defensive copy pattern).
     *
     * Time complexity: O(n) — we have to visit every item once.
     *
     * @return A new List containing all cached items (may be empty, never null)
     */
    public List<T> getAll() {
        // store.values() returns a Collection view of all values.
        // new ArrayList<>(...) copies them into a fresh, independent list.
        return new ArrayList<>(store.values());
    }

    /**
     * Returns the number of items currently in the cache.
     *
     * Time complexity: O(1) — HashMap tracks size internally.
     *
     * @return The number of entries in the cache
     */
    public int size() {
        return store.size();
    }

    /**
     * Clears all entries from the cache.
     * Useful when resetting the session or running tests.
     */
    public void clear() {
        store.clear();
    }

    /**
     * Returns a human-readable summary of the cache's current state.
     */
    @Override
    public String toString() {
        return "CacheStore[size=" + store.size() + ", entries=" + store.keySet() + "]";
    }
}
