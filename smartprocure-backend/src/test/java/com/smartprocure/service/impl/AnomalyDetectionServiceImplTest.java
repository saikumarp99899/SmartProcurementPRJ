package com.smartprocure.service.impl;

import com.smartprocure.dto.response.AnomalyResponse;
import com.smartprocure.entity.*;
import com.smartprocure.entity.PurchaseOrder.PurchaseOrderStatus;
import com.smartprocure.repository.PurchaseOrderItemRepository;
import com.smartprocure.repository.PurchaseOrderRepository;
import com.smartprocure.repository.RequisitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for AnomalyDetectionServiceImpl.
 * Tests price anomaly detection, split ordering detection, vendor concentration detection,
 * severity classification, and minimum data points guard.
 */
@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceImplTest {

    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Mock
    private RequisitionRepository requisitionRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @InjectMocks
    private AnomalyDetectionServiceImpl anomalyDetectionService;

    @Nested
    @DisplayName("Price Anomaly Detection")
    class PriceAnomalyTests {

        @Test
        @DisplayName("Should detect price anomaly when item exceeds 2σ above mean")
        void detectsPriceAnomaly_WhenPriceExceedsTwoSigma() {
            // Arrange: 5 items at $10 each, 1 item at $100 (well above 2σ)
            List<PurchaseOrderItem> items = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                items.add(createPOItem((long) (i + 1), "Widget", new BigDecimal("10.00"), 1));
            }
            items.add(createPOItem(6L, "Widget", new BigDecimal("100.00"), 1));

            when(purchaseOrderItemRepository.findAll()).thenReturn(items);
            when(requisitionRepository.findAll()).thenReturn(Collections.emptyList());
            when(purchaseOrderRepository.findAll()).thenReturn(Collections.emptyList());

            // Act
            List<AnomalyResponse> anomalies = anomalyDetectionService.detectAnomalies();

            // Assert
            List<AnomalyResponse> priceAnomalies = anomalies.stream()
                    .filter(a -> "PRICE".equals(a.getType()))
                    .toList();
            assertThat(priceAnomalies).isNotEmpty();
            assertThat(priceAnomalies.get(0).getDescription()).contains("Widget");
            assertThat(priceAnomalies.get(0).getDescription()).contains("100.00");
        }

        @Test
        @DisplayName("Should not flag price anomaly when price is within 2σ")
        void noPriceAnomaly_WhenPriceWithinTwoSigma() {
            // Arrange: 6 items with similar prices (low variance)
            List<PurchaseOrderItem> items = new ArrayList<>();
            items.add(createPOItem(1L, "Pencil", new BigDecimal("5.00"), 1));
            items.add(createPOItem(2L, "Pencil", new BigDecimal("5.50"), 1));
            items.add(createPOItem(3L, "Pencil", new BigDecimal("4.80"), 1));
            items.add(createPOItem(4L, "Pencil", new BigDecimal("5.20"), 1));
            items.add(createPOItem(5L, "Pencil", new BigDecimal("5.10"), 1));
            items.add(createPOItem(6L, "Pencil", new BigDecimal("5.30"), 1));

            when(purchaseOrderItemRepository.findAll()).thenReturn(items);
            when(requisitionRepository.findAll()).thenReturn(Collections.emptyList());
            when(purchaseOrderRepository.findAll()).thenReturn(Collections.emptyList());

            // Act
            List<AnomalyResponse> anomalies = anomalyDetectionService.detectAnomalies();

            // Assert
            List<AnomalyResponse> priceAnomalies = anomalies.stream()
                    .filter(a -> "PRICE".equals(a.getType()))
                    .toList();
            assertThat(priceAnomalies).isEmpty();
        }

        @Test
        @DisplayName("Should skip categories with fewer than 5 data points")
        void skipsCategoriesWithFewerThanFiveDataPoints() {
            // Arrange: only 4 items in category
            List<PurchaseOrderItem> items = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                items.add(createPOItem((long) (i + 1), "RareItem", new BigDecimal("10.00"), 1));
            }
            items.add(createPOItem(4L, "RareItem", new BigDecimal("500.00"), 1)); // outlier but < 5 data points

            when(purchaseOrderItemRepository.findAll()).thenReturn(items);
            when(requisitionRepository.findAll()).thenReturn(Collections.emptyList());
            when(purchaseOrderRepository.findAll()).thenReturn(Collections.emptyList());

            // Act
            List<AnomalyResponse> anomalies = anomalyDetectionService.detectAnomalies();

            // Assert
            List<AnomalyResponse> priceAnomalies = anomalies.stream()
                    .filter(a -> "PRICE".equals(a.getType()))
                    .toList();
            assertThat(priceAnomalies).isEmpty();
        }
    }

    @Nested
    @DisplayName("Split Ordering Detection")
    class SplitOrderingTests {

        @Test
        @DisplayName("Should detect split ordering when 3+ requisitions in 7 days below threshold with combined above")
        void detectsSplitOrdering() {
            // Arrange: 3 requisitions within 7 days, each below $5000, combined > $5000
            User requester = createUser(1L, "John", "Doe");
            LocalDateTime baseDate = LocalDateTime.now().minusDays(3);

            List<Requisition> requisitions = new ArrayList<>();
            requisitions.add(createRequisition(1L, requester, new BigDecimal("2000"), baseDate));
            requisitions.add(createRequisition(2L, requester, new BigDecimal("2500"), baseDate.plusDays(1)));
            requisitions.add(createRequisition(3L, requester, new BigDecimal("1800"), baseDate.plusDays(2)));

            when(purchaseOrderItemRepository.findAll()).thenReturn(Collections.emptyList());
            when(requisitionRepository.findAll()).thenReturn(requisitions);
            when(purchaseOrderRepository.findAll()).thenReturn(Collections.emptyList());

            // Act
            List<AnomalyResponse> anomalies = anomalyDetectionService.detectAnomalies();

            // Assert
            List<AnomalyResponse> splitAnomalies = anomalies.stream()
                    .filter(a -> "SPLIT_ORDER".equals(a.getType()))
                    .toList();
            assertThat(splitAnomalies).hasSize(1);
            assertThat(splitAnomalies.get(0).getDescription()).contains("John Doe");
            assertThat(splitAnomalies.get(0).getDescription()).contains("3 requisitions");
            assertThat(splitAnomalies.get(0).getAffectedEntities()).hasSize(3);
        }

        @Test
        @DisplayName("Should not flag split ordering when fewer than 3 requisitions in window")
        void noSplitOrdering_WhenFewerThanThreeRequisitions() {
            // Arrange: only 2 requisitions
            User requester = createUser(1L, "Jane", "Smith");
            LocalDateTime baseDate = LocalDateTime.now().minusDays(3);

            List<Requisition> requisitions = new ArrayList<>();
            requisitions.add(createRequisition(1L, requester, new BigDecimal("2000"), baseDate));
            requisitions.add(createRequisition(2L, requester, new BigDecimal("4000"), baseDate.plusDays(1)));

            when(purchaseOrderItemRepository.findAll()).thenReturn(Collections.emptyList());
            when(requisitionRepository.findAll()).thenReturn(requisitions);
            when(purchaseOrderRepository.findAll()).thenReturn(Collections.emptyList());

            // Act
            List<AnomalyResponse> anomalies = anomalyDetectionService.detectAnomalies();

            // Assert
            List<AnomalyResponse> splitAnomalies = anomalies.stream()
                    .filter(a -> "SPLIT_ORDER".equals(a.getType()))
                    .toList();
            assertThat(splitAnomalies).isEmpty();
        }

        @Test
        @DisplayName("Should not flag split ordering when combined total is below threshold")
        void noSplitOrdering_WhenCombinedBelowThreshold() {
            // Arrange: 3 requisitions but combined < $5000
            User requester = createUser(1L, "Bob", "Brown");
            LocalDateTime baseDate = LocalDateTime.now().minusDays(3);

            List<Requisition> requisitions = new ArrayList<>();
            requisitions.add(createRequisition(1L, requester, new BigDecimal("1000"), baseDate));
            requisitions.add(createRequisition(2L, requester, new BigDecimal("1000"), baseDate.plusDays(1)));
            requisitions.add(createRequisition(3L, requester, new BigDecimal("1000"), baseDate.plusDays(2)));

            when(purchaseOrderItemRepository.findAll()).thenReturn(Collections.emptyList());
            when(requisitionRepository.findAll()).thenReturn(requisitions);
            when(purchaseOrderRepository.findAll()).thenReturn(Collections.emptyList());

            // Act
            List<AnomalyResponse> anomalies = anomalyDetectionService.detectAnomalies();

            // Assert
            List<AnomalyResponse> splitAnomalies = anomalies.stream()
                    .filter(a -> "SPLIT_ORDER".equals(a.getType()))
                    .toList();
            assertThat(splitAnomalies).isEmpty();
        }

        @Test
        @DisplayName("Should not flag when requisitions are outside 7-day window")
        void noSplitOrdering_WhenOutsideSevenDayWindow() {
            // Arrange: 3 requisitions spread over more than 7 days
            User requester = createUser(1L, "Alice", "Jones");
            LocalDateTime baseDate = LocalDateTime.now().minusDays(20);

            List<Requisition> requisitions = new ArrayList<>();
            requisitions.add(createRequisition(1L, requester, new BigDecimal("2000"), baseDate));
            requisitions.add(createRequisition(2L, requester, new BigDecimal("2000"), baseDate.plusDays(5)));
            requisitions.add(createRequisition(3L, requester, new BigDecimal("2000"), baseDate.plusDays(10)));

            when(purchaseOrderItemRepository.findAll()).thenReturn(Collections.emptyList());
            when(requisitionRepository.findAll()).thenReturn(requisitions);
            when(purchaseOrderRepository.findAll()).thenReturn(Collections.emptyList());

            // Act
            List<AnomalyResponse> anomalies = anomalyDetectionService.detectAnomalies();

            // Assert
            List<AnomalyResponse> splitAnomalies = anomalies.stream()
                    .filter(a -> "SPLIT_ORDER".equals(a.getType()))
                    .toList();
            assertThat(splitAnomalies).isEmpty();
        }
    }

    @Nested
    @DisplayName("Vendor Concentration Detection")
    class VendorConcentrationTests {

        @Test
        @DisplayName("Should detect vendor concentration when single vendor exceeds 60% of category spend")
        void detectsVendorConcentration() {
            // Arrange: one vendor has 80% of spend for a category
            Vendor vendor1 = createVendor(1L, "Big Vendor");
            Vendor vendor2 = createVendor(2L, "Small Vendor");

            // 5 items from vendor1 ($800 total) and 1 item from vendor2 ($200)
            List<PurchaseOrder> orders = new ArrayList<>();
            orders.add(createPurchaseOrder(1L, vendor1, LocalDate.now().minusDays(10),
                    List.of(createPOItemWithTotal("Paper", new BigDecimal("100.00"), new BigDecimal("100.00")))));
            orders.add(createPurchaseOrder(2L, vendor1, LocalDate.now().minusDays(20),
                    List.of(createPOItemWithTotal("Paper", new BigDecimal("200.00"), new BigDecimal("200.00")))));
            orders.add(createPurchaseOrder(3L, vendor1, LocalDate.now().minusDays(30),
                    List.of(createPOItemWithTotal("Paper", new BigDecimal("150.00"), new BigDecimal("150.00")))));
            orders.add(createPurchaseOrder(4L, vendor1, LocalDate.now().minusDays(40),
                    List.of(createPOItemWithTotal("Paper", new BigDecimal("200.00"), new BigDecimal("200.00")))));
            orders.add(createPurchaseOrder(5L, vendor1, LocalDate.now().minusDays(50),
                    List.of(createPOItemWithTotal("Paper", new BigDecimal("150.00"), new BigDecimal("150.00")))));
            orders.add(createPurchaseOrder(6L, vendor2, LocalDate.now().minusDays(15),
                    List.of(createPOItemWithTotal("Paper", new BigDecimal("200.00"), new BigDecimal("200.00")))));

            when(purchaseOrderItemRepository.findAll()).thenReturn(Collections.emptyList());
            when(requisitionRepository.findAll()).thenReturn(Collections.emptyList());
            when(purchaseOrderRepository.findAll()).thenReturn(orders);

            // Act
            List<AnomalyResponse> anomalies = anomalyDetectionService.detectAnomalies();

            // Assert
            List<AnomalyResponse> concentrationAnomalies = anomalies.stream()
                    .filter(a -> "VENDOR_CONCENTRATION".equals(a.getType()))
                    .toList();
            assertThat(concentrationAnomalies).isNotEmpty();
            assertThat(concentrationAnomalies.get(0).getDescription()).contains("Big Vendor");
            assertThat(concentrationAnomalies.get(0).getDescription()).containsIgnoringCase("paper");
        }

        @Test
        @DisplayName("Should not flag vendor concentration when no vendor exceeds 60%")
        void noVendorConcentration_WhenBalanced() {
            // Arrange: even distribution between vendors
            Vendor vendor1 = createVendor(1L, "Vendor A");
            Vendor vendor2 = createVendor(2L, "Vendor B");
            Vendor vendor3 = createVendor(3L, "Vendor C");

            List<PurchaseOrder> orders = new ArrayList<>();
            // 2 items each from 3 vendors (33% each) - need at least 5 data points total
            orders.add(createPurchaseOrder(1L, vendor1, LocalDate.now().minusDays(10),
                    List.of(createPOItemWithTotal("Toner", new BigDecimal("100.00"), new BigDecimal("100.00")))));
            orders.add(createPurchaseOrder(2L, vendor1, LocalDate.now().minusDays(20),
                    List.of(createPOItemWithTotal("Toner", new BigDecimal("100.00"), new BigDecimal("100.00")))));
            orders.add(createPurchaseOrder(3L, vendor2, LocalDate.now().minusDays(30),
                    List.of(createPOItemWithTotal("Toner", new BigDecimal("100.00"), new BigDecimal("100.00")))));
            orders.add(createPurchaseOrder(4L, vendor2, LocalDate.now().minusDays(40),
                    List.of(createPOItemWithTotal("Toner", new BigDecimal("100.00"), new BigDecimal("100.00")))));
            orders.add(createPurchaseOrder(5L, vendor3, LocalDate.now().minusDays(50),
                    List.of(createPOItemWithTotal("Toner", new BigDecimal("100.00"), new BigDecimal("100.00")))));

            when(purchaseOrderItemRepository.findAll()).thenReturn(Collections.emptyList());
            when(requisitionRepository.findAll()).thenReturn(Collections.emptyList());
            when(purchaseOrderRepository.findAll()).thenReturn(orders);

            // Act
            List<AnomalyResponse> anomalies = anomalyDetectionService.detectAnomalies();

            // Assert
            List<AnomalyResponse> concentrationAnomalies = anomalies.stream()
                    .filter(a -> "VENDOR_CONCENTRATION".equals(a.getType()))
                    .toList();
            assertThat(concentrationAnomalies).isEmpty();
        }

        @Test
        @DisplayName("Should skip vendor concentration check for categories with fewer than 5 data points")
        void skipsConcentration_WhenFewerThanFiveDataPoints() {
            // Arrange: only 3 items in category
            Vendor vendor1 = createVendor(1L, "Dominant Vendor");

            List<PurchaseOrder> orders = new ArrayList<>();
            orders.add(createPurchaseOrder(1L, vendor1, LocalDate.now().minusDays(10),
                    List.of(createPOItemWithTotal("Special Item", new BigDecimal("5000.00"), new BigDecimal("5000.00")))));
            orders.add(createPurchaseOrder(2L, vendor1, LocalDate.now().minusDays(20),
                    List.of(createPOItemWithTotal("Special Item", new BigDecimal("5000.00"), new BigDecimal("5000.00")))));
            orders.add(createPurchaseOrder(3L, vendor1, LocalDate.now().minusDays(30),
                    List.of(createPOItemWithTotal("Special Item", new BigDecimal("5000.00"), new BigDecimal("5000.00")))));

            when(purchaseOrderItemRepository.findAll()).thenReturn(Collections.emptyList());
            when(requisitionRepository.findAll()).thenReturn(Collections.emptyList());
            when(purchaseOrderRepository.findAll()).thenReturn(orders);

            // Act
            List<AnomalyResponse> anomalies = anomalyDetectionService.detectAnomalies();

            // Assert
            List<AnomalyResponse> concentrationAnomalies = anomalies.stream()
                    .filter(a -> "VENDOR_CONCENTRATION".equals(a.getType()))
                    .toList();
            assertThat(concentrationAnomalies).isEmpty();
        }
    }

    @Nested
    @DisplayName("Severity Classification")
    class SeverityTests {

        @Test
        @DisplayName("Should assign HIGH severity when impact exceeds 10,000")
        void highSeverity_WhenHighImpact() {
            String severity = anomalyDetectionService.classifySeverity(new BigDecimal("15000"), 0);
            assertThat(severity).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("Should assign HIGH severity when deviation exceeds 4σ")
        void highSeverity_WhenHighDeviation() {
            String severity = anomalyDetectionService.classifySeverity(new BigDecimal("500"), 4.5);
            assertThat(severity).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("Should assign MEDIUM severity when impact is between 2,000 and 10,000")
        void mediumSeverity_WhenMediumImpact() {
            String severity = anomalyDetectionService.classifySeverity(new BigDecimal("5000"), 0);
            assertThat(severity).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("Should assign MEDIUM severity when deviation is between 2 and 4σ")
        void mediumSeverity_WhenMediumDeviation() {
            String severity = anomalyDetectionService.classifySeverity(new BigDecimal("500"), 3.0);
            assertThat(severity).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("Should assign LOW severity for small impact and low deviation")
        void lowSeverity_WhenLowImpactAndDeviation() {
            String severity = anomalyDetectionService.classifySeverity(new BigDecimal("500"), 1.0);
            assertThat(severity).isEqualTo("LOW");
        }
    }

    @Nested
    @DisplayName("Empty Data Scenarios")
    class EmptyDataTests {

        @Test
        @DisplayName("Should return empty list when no data exists")
        void returnsEmptyList_WhenNoData() {
            when(purchaseOrderItemRepository.findAll()).thenReturn(Collections.emptyList());
            when(requisitionRepository.findAll()).thenReturn(Collections.emptyList());
            when(purchaseOrderRepository.findAll()).thenReturn(Collections.emptyList());

            List<AnomalyResponse> anomalies = anomalyDetectionService.detectAnomalies();

            assertThat(anomalies).isEmpty();
        }
    }

    // --- Helper methods ---

    private PurchaseOrderItem createPOItem(Long id, String itemName, BigDecimal unitPrice, int quantity) {
        return PurchaseOrderItem.builder()
                .id(id)
                .itemName(itemName)
                .unitPrice(unitPrice)
                .quantity(quantity)
                .totalPrice(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                .build();
    }

    private PurchaseOrderItem createPOItemWithTotal(String itemName, BigDecimal unitPrice, BigDecimal totalPrice) {
        return PurchaseOrderItem.builder()
                .itemName(itemName)
                .unitPrice(unitPrice)
                .quantity(1)
                .totalPrice(totalPrice)
                .build();
    }

    private User createUser(Long id, String firstName, String lastName) {
        return User.builder()
                .id(id)
                .firstName(firstName)
                .lastName(lastName)
                .email(firstName.toLowerCase() + "." + lastName.toLowerCase() + "@test.com")
                .password("encoded")
                .build();
    }

    private Requisition createRequisition(Long id, User requester, BigDecimal totalAmount, LocalDateTime createdAt) {
        Requisition req = Requisition.builder()
                .id(id)
                .requisitionNumber("REQ-2024-" + String.format("%04d", id))
                .requester(requester)
                .totalAmount(totalAmount)
                .build();
        // Set createdAt directly since @CreationTimestamp won't work in unit tests
        req.setCreatedAt(createdAt);
        return req;
    }

    private Vendor createVendor(Long id, String name) {
        return Vendor.builder()
                .id(id)
                .vendorCode("V" + String.format("%03d", id))
                .vendorName(name)
                .email(name.toLowerCase().replace(" ", "") + "@vendor.com")
                .build();
    }

    private PurchaseOrder createPurchaseOrder(Long id, Vendor vendor, LocalDate orderDate,
                                              List<PurchaseOrderItem> items) {
        PurchaseOrder po = PurchaseOrder.builder()
                .id(id)
                .poNumber("PO-2024-" + String.format("%04d", id))
                .vendor(vendor)
                .orderDate(orderDate)
                .status(PurchaseOrderStatus.SENT)
                .items(new ArrayList<>(items))
                .build();
        return po;
    }
}
