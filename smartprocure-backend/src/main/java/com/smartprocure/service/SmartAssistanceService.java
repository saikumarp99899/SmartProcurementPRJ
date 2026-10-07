package com.smartprocure.service;

import com.smartprocure.dto.response.PriceSuggestionResponse;

import java.util.List;

/**
 * AI-powered Smart Requisition Assistance Service.
 *
 * Provides item name autocomplete suggestions from historical procurement data
 * and price suggestions based on recent purchase history.
 */
public interface SmartAssistanceService {

    /**
     * Returns up to 10 distinct item name suggestions matching the given prefix
     * (case-insensitive). Returns an empty list if prefix is shorter than 2 characters.
     *
     * @param prefix the search prefix typed by the user
     * @return list of matching item names (max 10, deduplicated across sources)
     */
    List<String> getItemSuggestions(String prefix);

    /**
     * Returns a price suggestion for the given item name based on the median
     * of the last 10 purchases. Returns insufficientData if fewer than 3 records exist.
     *
     * @param itemName the item name to look up pricing history for
     * @return price suggestion response with median, min, max and data point count
     */
    PriceSuggestionResponse getPriceSuggestion(String itemName);
}
