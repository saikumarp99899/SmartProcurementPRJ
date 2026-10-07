package com.smartprocure.service.impl;

import com.smartprocure.dto.response.VendorRecommendationResponse;
import com.smartprocure.entity.PurchaseOrder;
import com.smartprocure.entity.PurchaseOrder.PurchaseOrderStatus;
import com.smartprocure.entity.PurchaseOrderItem;
import com.smartprocure.entity.Vendor;
import com.smartprocure.repository.PurchaseOrderItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for VendorRecommendationServiceImpl.
 * Verifies vendor recommendation scoring, sorting, and result limits.
 */
@ExtendWith(MockitoExtension.class)
class VendorRecommendationServiceImplTest {

    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @InjectMocks
    private VendorRecommendationServiceImpl vendorRecommendationService;

    @Test
    @DisplayName("Should return empty list when no matching items found")
    void getRecommendations_NoMatchingItems_ReturnsEmptyList() {
        // Arrange
        when(purchaseOrderItemRepository.findByItemNameContainingIgnoreCase("laptop"))
                .thenReturn(Collections.emptyList());

        // Act
        List<VendorRecommendationResponse> result = vendorRecommendationService.getRecommendations("laptop");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return recommendations sorted by score descending")
    void getRecommendations_MultipleVendors_ReturnsSortedByScore() {
        // Arrange
        Vendor cheapVendor = buildVendor(1L, "CheapVendor", "V001", new BigDecimal("4.50"));
        Vendor expensiveVendor = buildVendor(2L, "ExpensiveVendor", "V002", new BigDecimal("3.00"));

        PurchaseOrder po1 = buildPO(cheapVendor, PurchaseOrderStatus.DELIVERED, LocalDate.now().plusDays(5));
        PurchaseOrder po2 = buildPO(expensiveVendor, PurchaseOrderStatus.DELIVERED, LocalDate.now().plusDays(5));

        PurchaseOrderItem item1 = buildItem(po1, "Laptop", new BigDecimal("800.00"), 2);
        PurchaseOrderItem item2 = buildItem(po2, "Laptop Pro", new BigDecimal("1500.00"), 1);

        when(purchaseOrderItemRepository.findByItemNameContainingIgnoreCase("laptop"))
                .thenReturn(List.of(item1, item2));

        // Act
        List<VendorRecommendationResponse> result = vendorRecommendationService.getRecommendations("laptop");

        // Assert
        assertThat(result).hasSize(2);
        // CheapVendor should rank higher (better price + higher rating)
        assertThat(result.get(0).getVendorName()).isEqualTo("CheapVendor");
        assertThat(result.get(1).getVendorName()).isEqualTo("ExpensiveVendor");
        // Both scores should be within 0-100
        assertThat(result.get(0).getConfidenceScore()).isBetween(0, 100);
        assertThat(result.get(1).getConfidenceScore()).isBetween(0, 100);
        // Sorted descending
        assertThat(result.get(0).getConfidenceScore())
                .isGreaterThanOrEqualTo(result.get(1).getConfidenceScore());
    }

    @Test
    @DisplayName("Should return at most 5 recommendations")
    void getRecommendations_MoreThan5Vendors_ReturnsMax5() {
        // Arrange - create 7 vendors with items
        List<PurchaseOrderItem> items = new java.util.ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            Vendor vendor = buildVendor((long) i, "Vendor" + i, "V00" + i,
                    new BigDecimal("3.00").add(BigDecimal.valueOf(i * 0.2)));
            PurchaseOrder po = buildPO(vendor, PurchaseOrderStatus.DELIVERED, LocalDate.now().plusDays(5));
            items.add(buildItem(po, "Office Chair", new BigDecimal(100 + i * 10), 1));
        }

        when(purchaseOrderItemRepository.findByItemNameContainingIgnoreCase("chair"))
                .thenReturn(items);

        // Act
        List<VendorRecommendationResponse> result = vendorRecommendationService.getRecommendations("chair");

        // Assert
        assertThat(result).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Should include reasoning text for each recommendation")
    void getRecommendations_ValidData_IncludesReasoning() {
        // Arrange
        Vendor vendor = buildVendor(1L, "TopVendor", "V001", new BigDecimal("4.00"));
        PurchaseOrder po = buildPO(vendor, PurchaseOrderStatus.DELIVERED, LocalDate.now().plusDays(10));
        PurchaseOrderItem item = buildItem(po, "Monitor", new BigDecimal("250.00"), 3);

        when(purchaseOrderItemRepository.findByItemNameContainingIgnoreCase("monitor"))
                .thenReturn(List.of(item));

        // Act
        List<VendorRecommendationResponse> result = vendorRecommendationService.getRecommendations("monitor");

        // Assert
        assertThat(result).hasSize(1);
        VendorRecommendationResponse recommendation = result.get(0);
        assertThat(recommendation.getReasoning()).isNotBlank();
        assertThat(recommendation.getVendorId()).isEqualTo(1L);
        assertThat(recommendation.getVendorName()).isEqualTo("TopVendor");
        assertThat(recommendation.getVendorCode()).isEqualTo("V001");
        assertThat(recommendation.getAveragePrice()).isEqualByComparingTo("250.00");
        assertThat(recommendation.getRating()).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("Should calculate delivery performance correctly")
    void getRecommendations_MixedDeliveryStatus_CalculatesPerformance() {
        // Arrange
        Vendor vendor = buildVendor(1L, "MixedVendor", "V001", new BigDecimal("3.50"));

        // PO delivered on time
        PurchaseOrder po1 = buildPO(vendor, PurchaseOrderStatus.DELIVERED, LocalDate.now().plusDays(30));
        PurchaseOrderItem item1 = buildItem(po1, "Pen", new BigDecimal("5.00"), 100);

        // PO still open but not overdue
        PurchaseOrder po2 = buildPO(vendor, PurchaseOrderStatus.SENT, LocalDate.now().plusDays(10));
        PurchaseOrderItem item2 = buildItem(po2, "Pen Pack", new BigDecimal("4.50"), 200);

        when(purchaseOrderItemRepository.findByItemNameContainingIgnoreCase("pen"))
                .thenReturn(List.of(item1, item2));

        // Act
        List<VendorRecommendationResponse> result = vendorRecommendationService.getRecommendations("pen");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDeliveryPerformance()).isBetween(0, 100);
    }

    @Test
    @DisplayName("Should handle vendor with null rating gracefully")
    void getRecommendations_NullRating_HandlesGracefully() {
        // Arrange
        Vendor vendor = buildVendor(1L, "NoRatingVendor", "V001", null);
        PurchaseOrder po = buildPO(vendor, PurchaseOrderStatus.DELIVERED, LocalDate.now().plusDays(5));
        PurchaseOrderItem item = buildItem(po, "Keyboard", new BigDecimal("45.00"), 5);

        when(purchaseOrderItemRepository.findByItemNameContainingIgnoreCase("keyboard"))
                .thenReturn(List.of(item));

        // Act
        List<VendorRecommendationResponse> result = vendorRecommendationService.getRecommendations("keyboard");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getConfidenceScore()).isBetween(0, 100);
        assertThat(result.get(0).getRating()).isEqualByComparingTo("0");
    }

    // --- Helper methods ---

    private Vendor buildVendor(Long id, String name, String code, BigDecimal rating) {
        return Vendor.builder()
                .id(id)
                .vendorName(name)
                .vendorCode(code)
                .rating(rating)
                .build();
    }

    private PurchaseOrder buildPO(Vendor vendor, PurchaseOrderStatus status, LocalDate expectedDelivery) {
        return PurchaseOrder.builder()
                .id(vendor.getId())
                .vendor(vendor)
                .status(status)
                .orderDate(LocalDate.now().minusDays(30))
                .expectedDeliveryDate(expectedDelivery)
                .totalAmount(BigDecimal.ZERO)
                .build();
    }

    private PurchaseOrderItem buildItem(PurchaseOrder po, String itemName, BigDecimal unitPrice, int qty) {
        return PurchaseOrderItem.builder()
                .id(po.getId())
                .purchaseOrder(po)
                .itemName(itemName)
                .unitPrice(unitPrice)
                .quantity(qty)
                .totalPrice(unitPrice.multiply(BigDecimal.valueOf(qty)))
                .build();
    }
}
