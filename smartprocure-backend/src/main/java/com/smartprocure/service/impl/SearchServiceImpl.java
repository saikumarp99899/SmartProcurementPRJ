package com.smartprocure.service.impl;

import com.smartprocure.dto.response.SearchResponse;
import com.smartprocure.dto.response.SearchResult;
import com.smartprocure.entity.*;
import com.smartprocure.entity.Role.RoleName;
import com.smartprocure.exception.BadRequestException;
import com.smartprocure.repository.*;
import com.smartprocure.security.user.UserPrincipal;
import com.smartprocure.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Cross-entity search service with role-based result filtering.
 *
 * Search targets:
 * - Requisitions: number, description
 * - Purchase Orders: PO number
 * - Vendors: name, code
 * - Items: name (from both requisition items and PO items)
 *
 * Role-based filtering:
 * - ADMIN: sees all entities
 * - BUYER: sees own requisitions + all POs/vendors/items
 * - APPROVER: sees assigned requisitions + all POs/vendors/items
 */
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final int MAX_RESULTS_PER_CATEGORY = 5;

    private final RequisitionRepository requisitionRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final VendorRepository vendorRepository;
    private final RequisitionItemRepository requisitionItemRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Override
    @Transactional(readOnly = true)
    public SearchResponse search(String query, UserPrincipal currentUser) {
        validateQuery(query);

        String trimmedQuery = query.trim();
        Pageable limit = PageRequest.of(0, MAX_RESULTS_PER_CATEGORY);

        List<SearchResult> requisitions = searchRequisitions(trimmedQuery, currentUser, limit);
        List<SearchResult> purchaseOrders = searchPurchaseOrders(trimmedQuery, limit);
        List<SearchResult> vendors = searchVendors(trimmedQuery, limit);
        List<SearchResult> items = searchItems(trimmedQuery, limit);

        return SearchResponse.builder()
                .requisitions(requisitions)
                .purchaseOrders(purchaseOrders)
                .vendors(vendors)
                .items(items)
                .build();
    }

    private void validateQuery(String query) {
        if (query == null || query.trim().length() < 2 || query.trim().length() > 100) {
            throw new BadRequestException("Query must be between 2 and 100 characters");
        }
    }

    /**
     * Search requisitions with role-based filtering:
     * - ADMIN: all requisitions
     * - BUYER: only own requisitions
     * - APPROVER: only requisitions assigned to them
     */
    private List<SearchResult> searchRequisitions(String query, UserPrincipal currentUser, Pageable limit) {
        Set<RoleName> userRoles = extractRoleNames(currentUser);

        List<Requisition> results;

        if (userRoles.contains(RoleName.ADMIN)) {
            // Admin sees all requisitions
            results = requisitionRepository.searchByNumberOrDescription(query, limit);
        } else if (userRoles.contains(RoleName.BUYER)) {
            // Buyer sees only own requisitions
            results = requisitionRepository.searchByNumberOrDescriptionAndRequesterId(
                    query, currentUser.getId(), limit);
        } else if (userRoles.contains(RoleName.APPROVER)) {
            // Approver sees only requisitions assigned to them
            results = requisitionRepository.searchAssignedToApprover(
                    query, currentUser.getId(), userRoles, limit);
        } else {
            results = Collections.emptyList();
        }

        return results.stream()
                .map(r -> SearchResult.builder()
                        .id(r.getId())
                        .title(r.getRequisitionNumber())
                        .subtitle(r.getDescription() != null ? r.getDescription() : "")
                        .entityType("REQUISITION")
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Search purchase orders — all authenticated users can see POs.
     */
    private List<SearchResult> searchPurchaseOrders(String query, Pageable limit) {
        List<PurchaseOrder> results = purchaseOrderRepository.searchByPoNumber(query, limit);

        return results.stream()
                .map(po -> SearchResult.builder()
                        .id(po.getId())
                        .title(po.getPoNumber())
                        .subtitle(po.getVendor() != null ? po.getVendor().getVendorName() : "")
                        .entityType("PURCHASE_ORDER")
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Search vendors by name or code — all authenticated users can see vendors.
     */
    private List<SearchResult> searchVendors(String query, Pageable limit) {
        List<Vendor> results = vendorRepository.searchByNameOrCode(query, limit);

        return results.stream()
                .map(v -> SearchResult.builder()
                        .id(v.getId())
                        .title(v.getVendorName())
                        .subtitle(v.getVendorCode())
                        .entityType("VENDOR")
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Search items by name across both requisition items and PO items.
     * Deduplicates by item name and limits to MAX_RESULTS_PER_CATEGORY.
     */
    private List<SearchResult> searchItems(String query, Pageable limit) {
        List<RequisitionItem> reqItems = requisitionItemRepository.searchByItemName(query, limit);
        List<PurchaseOrderItem> poItems = purchaseOrderItemRepository.searchByItemName(query, limit);

        // Combine and deduplicate by item name, take up to limit
        return Stream.concat(
                        reqItems.stream().map(ri -> SearchResult.builder()
                                .id(ri.getId())
                                .title(ri.getItemName())
                                .subtitle("Requisition Item")
                                .entityType("ITEM")
                                .build()),
                        poItems.stream().map(poi -> SearchResult.builder()
                                .id(poi.getId())
                                .title(poi.getItemName())
                                .subtitle("Purchase Order Item")
                                .entityType("ITEM")
                                .build()))
                .distinct()
                .limit(MAX_RESULTS_PER_CATEGORY)
                .collect(Collectors.toList());
    }

    /**
     * Extract RoleName values from the user's granted authorities.
     */
    private Set<RoleName> extractRoleNames(UserPrincipal currentUser) {
        return currentUser.getAuthorities().stream()
                .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                .map(name -> {
                    try {
                        return RoleName.valueOf(name);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(r -> r != null)
                .collect(Collectors.toSet());
    }
}
