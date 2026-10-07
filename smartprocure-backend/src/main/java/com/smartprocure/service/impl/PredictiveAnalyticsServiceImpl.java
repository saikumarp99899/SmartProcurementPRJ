package com.smartprocure.service.impl;

import com.smartprocure.dto.response.*;
import com.smartprocure.entity.PurchaseOrder;
import com.smartprocure.entity.PurchaseOrder.PurchaseOrderStatus;
import com.smartprocure.entity.PurchaseOrderItem;
import com.smartprocure.repository.PurchaseOrderRepository;
import com.smartprocure.service.PredictiveAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Rule-based predictive analytics implementation.
 *
 * Spend Forecast Algorithm:
 * 1. Aggregate historical PO data by month
 * 2. Apply linear regression to identify trend (slope + intercept)
 * 3. Calculate seasonal indices from historical patterns
 * 4. Project 6 months ahead: predicted = (slope * futureIndex + intercept) * seasonalFactor
 * 5. Compute 80% confidence intervals using residual standard error
 *
 * Demand Forecast Algorithm:
 * 1. Identify top 10 item categories by total spend
 * 2. For each category, aggregate monthly demand (quantity + spend)
 * 3. Apply same linear + seasonal model per category
 * 4. Skip categories with < 3 months of data (insufficientData flag)
 * 5. Flag low-confidence when rolling 3-month accuracy < 70%
 */
@Service
@RequiredArgsConstructor
public class PredictiveAnalyticsServiceImpl implements PredictiveAnalyticsService {

    private static final int FORECAST_MONTHS = 6;
    private static final int MINIMUM_HISTORY_MONTHS = 3;
    private static final double CONFIDENCE_LEVEL_80_Z = 1.28; // z-score for 80% CI
    private static final double LOW_CONFIDENCE_THRESHOLD = 0.70;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final PurchaseOrderRepository purchaseOrderRepository;

    @Override
    @Transactional(readOnly = true)
    public SpendForecastResponse getSpendForecast() {
        List<PurchaseOrder> allOrders = purchaseOrderRepository.findAll().stream()
                .filter(po -> po.getStatus() != PurchaseOrderStatus.CANCELLED)
                .filter(po -> po.getOrderDate() != null)
                .toList();

        // Group spend by month
        Map<YearMonth, BigDecimal> monthlySpend = aggregateMonthlySpend(allOrders);

        // Build sorted historical data
        List<YearMonth> sortedMonths = monthlySpend.keySet().stream()
                .sorted()
                .toList();

        List<HistoricalDataPoint> historicalData = sortedMonths.stream()
                .map(ym -> HistoricalDataPoint.builder()
                        .month(ym.format(MONTH_FORMAT))
                        .actualSpend(monthlySpend.get(ym))
                        .orderCount(countOrdersForMonth(allOrders, ym))
                        .build())
                .toList();

        // Generate forecasts
        List<MonthlyForecast> forecasts;
        if (sortedMonths.size() < MINIMUM_HISTORY_MONTHS) {
            // Not enough data - return empty forecasts
            forecasts = Collections.emptyList();
        } else {
            double[] spendValues = sortedMonths.stream()
                    .mapToDouble(ym -> monthlySpend.get(ym).doubleValue())
                    .toArray();

            forecasts = generateSpendForecasts(sortedMonths, spendValues);
        }

        return SpendForecastResponse.builder()
                .forecasts(forecasts)
                .historicalData(historicalData)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DemandForecastResponse getDemandForecast() {
        List<PurchaseOrder> allOrders = purchaseOrderRepository.findAll().stream()
                .filter(po -> po.getStatus() != PurchaseOrderStatus.CANCELLED)
                .filter(po -> po.getOrderDate() != null)
                .toList();

        // Identify top 10 categories by total spend
        Map<String, List<PurchaseOrderItem>> itemsByCategory = allOrders.stream()
                .filter(po -> po.getItems() != null)
                .flatMap(po -> po.getItems().stream())
                .collect(Collectors.groupingBy(PurchaseOrderItem::getItemName));

        List<String> topCategories = itemsByCategory.entrySet().stream()
                .sorted((a, b) -> {
                    BigDecimal spendA = a.getValue().stream()
                            .map(PurchaseOrderItem::getTotalPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal spendB = b.getValue().stream()
                            .map(PurchaseOrderItem::getTotalPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return spendB.compareTo(spendA);
                })
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();

        // Generate forecast for each category
        List<CategoryDemandForecast> categories = topCategories.stream()
                .map(category -> buildCategoryForecast(category, allOrders))
                .toList();

        return DemandForecastResponse.builder()
                .categories(categories)
                .build();
    }

    // ========== Spend Forecast Helpers ==========

    private Map<YearMonth, BigDecimal> aggregateMonthlySpend(List<PurchaseOrder> orders) {
        Map<YearMonth, BigDecimal> result = new TreeMap<>();
        for (PurchaseOrder po : orders) {
            YearMonth ym = YearMonth.from(po.getOrderDate());
            result.merge(ym, po.getTotalAmount(), BigDecimal::add);
        }
        return result;
    }

    private int countOrdersForMonth(List<PurchaseOrder> orders, YearMonth ym) {
        return (int) orders.stream()
                .filter(po -> YearMonth.from(po.getOrderDate()).equals(ym))
                .count();
    }

    private List<MonthlyForecast> generateSpendForecasts(List<YearMonth> sortedMonths, double[] spendValues) {
        int n = spendValues.length;

        // Linear regression: y = slope * x + intercept
        double[] regression = computeLinearRegression(spendValues);
        double slope = regression[0];
        double intercept = regression[1];

        // Compute seasonal indices (ratio of actual to trend for each month-of-year)
        double[] seasonalIndices = computeSeasonalIndices(sortedMonths, spendValues, slope, intercept);

        // Compute residual standard error for confidence intervals
        double residualStdError = computeResidualStdError(spendValues, slope, intercept, seasonalIndices, sortedMonths);

        // Check low-confidence: compare last 3 months predicted vs actual
        boolean lowConfidence = isLowConfidence(spendValues, slope, intercept, seasonalIndices, sortedMonths);

        // Generate 6 months of forecasts
        YearMonth lastMonth = sortedMonths.get(sortedMonths.size() - 1);
        List<MonthlyForecast> forecasts = new ArrayList<>();

        for (int i = 1; i <= FORECAST_MONTHS; i++) {
            YearMonth forecastMonth = lastMonth.plusMonths(i);
            int futureIndex = n + i - 1; // 0-based index continuing from historical

            double trendValue = slope * futureIndex + intercept;
            int monthOfYear = forecastMonth.getMonthValue() - 1; // 0-based
            double seasonalFactor = seasonalIndices[monthOfYear];
            double predicted = trendValue * seasonalFactor;

            // Ensure predicted is non-negative
            predicted = Math.max(0, predicted);

            // 80% confidence interval
            double margin = CONFIDENCE_LEVEL_80_Z * residualStdError * Math.sqrt(1.0 + 1.0 / n);
            double lower = Math.max(0, predicted - margin);
            double upper = predicted + margin;

            forecasts.add(MonthlyForecast.builder()
                    .month(forecastMonth.format(MONTH_FORMAT))
                    .predictedSpend(BigDecimal.valueOf(predicted).setScale(2, RoundingMode.HALF_UP))
                    .lowerBound(BigDecimal.valueOf(lower).setScale(2, RoundingMode.HALF_UP))
                    .upperBound(BigDecimal.valueOf(upper).setScale(2, RoundingMode.HALF_UP))
                    .lowConfidence(lowConfidence)
                    .build());
        }

        return forecasts;
    }

    // ========== Demand Forecast Helpers ==========

    private CategoryDemandForecast buildCategoryForecast(String category, List<PurchaseOrder> allOrders) {
        // Gather monthly data for this category
        Map<YearMonth, int[]> monthlyData = new TreeMap<>(); // [quantity, spend_cents_placeholder]
        Map<YearMonth, BigDecimal> monthlySpendMap = new TreeMap<>();

        for (PurchaseOrder po : allOrders) {
            if (po.getOrderDate() == null || po.getItems() == null) continue;
            YearMonth ym = YearMonth.from(po.getOrderDate());

            for (PurchaseOrderItem item : po.getItems()) {
                if (category.equals(item.getItemName())) {
                    monthlyData.computeIfAbsent(ym, k -> new int[]{0});
                    monthlyData.get(ym)[0] += (item.getQuantity() != null ? item.getQuantity() : 0);
                    monthlySpendMap.merge(ym, item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO, BigDecimal::add);
                }
            }
        }

        List<YearMonth> sortedMonths = new ArrayList<>(monthlyData.keySet());
        Collections.sort(sortedMonths);

        // Build historical data
        List<MonthlyDemand> historical = sortedMonths.stream()
                .map(ym -> MonthlyDemand.builder()
                        .month(ym.format(MONTH_FORMAT))
                        .predictedQuantity(monthlyData.get(ym)[0])
                        .predictedSpend(monthlySpendMap.getOrDefault(ym, BigDecimal.ZERO))
                        .lowerBound(monthlySpendMap.getOrDefault(ym, BigDecimal.ZERO))
                        .upperBound(monthlySpendMap.getOrDefault(ym, BigDecimal.ZERO))
                        .build())
                .toList();

        // Minimum history guard: < 3 months → insufficientData
        if (sortedMonths.size() < MINIMUM_HISTORY_MONTHS) {
            return CategoryDemandForecast.builder()
                    .categoryName(category)
                    .forecasts(Collections.emptyList())
                    .historical(historical)
                    .insufficientData(true)
                    .build();
        }

        // Generate demand forecasts
        double[] quantityValues = sortedMonths.stream()
                .mapToDouble(ym -> monthlyData.get(ym)[0])
                .toArray();
        double[] spendValues = sortedMonths.stream()
                .mapToDouble(ym -> monthlySpendMap.getOrDefault(ym, BigDecimal.ZERO).doubleValue())
                .toArray();

        List<MonthlyDemand> forecasts = generateDemandForecasts(sortedMonths, quantityValues, spendValues);

        return CategoryDemandForecast.builder()
                .categoryName(category)
                .forecasts(forecasts)
                .historical(historical)
                .insufficientData(false)
                .build();
    }

    private List<MonthlyDemand> generateDemandForecasts(List<YearMonth> sortedMonths,
                                                         double[] quantityValues,
                                                         double[] spendValues) {
        int n = quantityValues.length;

        // Linear regression for quantity
        double[] qRegression = computeLinearRegression(quantityValues);
        double qSlope = qRegression[0];
        double qIntercept = qRegression[1];

        // Linear regression for spend
        double[] sRegression = computeLinearRegression(spendValues);
        double sSlope = sRegression[0];
        double sIntercept = sRegression[1];

        // Seasonal indices
        double[] qSeasonalIndices = computeSeasonalIndices(sortedMonths, quantityValues, qSlope, qIntercept);
        double[] sSeasonalIndices = computeSeasonalIndices(sortedMonths, spendValues, sSlope, sIntercept);

        // Residual standard errors
        double qResidualStdError = computeResidualStdError(quantityValues, qSlope, qIntercept, qSeasonalIndices, sortedMonths);
        double sResidualStdError = computeResidualStdError(spendValues, sSlope, sIntercept, sSeasonalIndices, sortedMonths);

        YearMonth lastMonth = sortedMonths.get(sortedMonths.size() - 1);
        List<MonthlyDemand> forecasts = new ArrayList<>();

        for (int i = 1; i <= FORECAST_MONTHS; i++) {
            YearMonth forecastMonth = lastMonth.plusMonths(i);
            int futureIndex = n + i - 1;
            int monthOfYear = forecastMonth.getMonthValue() - 1;

            // Quantity forecast
            double qTrend = qSlope * futureIndex + qIntercept;
            double qSeasonal = qSeasonalIndices[monthOfYear];
            double predictedQuantity = Math.max(0, qTrend * qSeasonal);

            // Spend forecast
            double sTrend = sSlope * futureIndex + sIntercept;
            double sSeasonal = sSeasonalIndices[monthOfYear];
            double predictedSpend = Math.max(0, sTrend * sSeasonal);

            // Confidence intervals for spend
            double sMargin = CONFIDENCE_LEVEL_80_Z * sResidualStdError * Math.sqrt(1.0 + 1.0 / n);
            double sLower = Math.max(0, predictedSpend - sMargin);
            double sUpper = predictedSpend + sMargin;

            forecasts.add(MonthlyDemand.builder()
                    .month(forecastMonth.format(MONTH_FORMAT))
                    .predictedQuantity((int) Math.round(predictedQuantity))
                    .predictedSpend(BigDecimal.valueOf(predictedSpend).setScale(2, RoundingMode.HALF_UP))
                    .lowerBound(BigDecimal.valueOf(sLower).setScale(2, RoundingMode.HALF_UP))
                    .upperBound(BigDecimal.valueOf(sUpper).setScale(2, RoundingMode.HALF_UP))
                    .build());
        }

        return forecasts;
    }

    // ========== Statistical Utilities ==========

    /**
     * Computes simple linear regression: y = slope * x + intercept
     * where x = 0, 1, 2, ... n-1 (index-based)
     *
     * @return double[]{slope, intercept}
     */
    private double[] computeLinearRegression(double[] values) {
        int n = values.length;
        if (n == 0) return new double[]{0, 0};
        if (n == 1) return new double[]{0, values[0]};

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values[i];
            sumXY += i * values[i];
            sumX2 += (double) i * i;
        }

        double denominator = n * sumX2 - sumX * sumX;
        if (denominator == 0) {
            return new double[]{0, sumY / n};
        }

        double slope = (n * sumXY - sumX * sumY) / denominator;
        double intercept = (sumY - slope * sumX) / n;

        return new double[]{slope, intercept};
    }

    /**
     * Computes seasonal indices (multiplicative model).
     * For each month-of-year (0-11), calculates the average ratio of
     * actual values to trend values.
     */
    private double[] computeSeasonalIndices(List<YearMonth> sortedMonths, double[] values,
                                            double slope, double intercept) {
        double[] seasonalSums = new double[12];
        int[] seasonalCounts = new int[12];

        for (int i = 0; i < values.length; i++) {
            double trendValue = slope * i + intercept;
            if (trendValue > 0) {
                int monthOfYear = sortedMonths.get(i).getMonthValue() - 1;
                seasonalSums[monthOfYear] += values[i] / trendValue;
                seasonalCounts[monthOfYear]++;
            }
        }

        double[] indices = new double[12];
        for (int m = 0; m < 12; m++) {
            if (seasonalCounts[m] > 0) {
                indices[m] = seasonalSums[m] / seasonalCounts[m];
            } else {
                indices[m] = 1.0; // default to no seasonal effect
            }
        }

        return indices;
    }

    /**
     * Computes the residual standard error between actual and fitted values.
     * Fitted = (slope * index + intercept) * seasonalFactor
     */
    private double computeResidualStdError(double[] values, double slope, double intercept,
                                           double[] seasonalIndices, List<YearMonth> sortedMonths) {
        int n = values.length;
        if (n <= 2) return 0;

        double sumSquaredResiduals = 0;
        for (int i = 0; i < n; i++) {
            int monthOfYear = sortedMonths.get(i).getMonthValue() - 1;
            double fitted = (slope * i + intercept) * seasonalIndices[monthOfYear];
            double residual = values[i] - fitted;
            sumSquaredResiduals += residual * residual;
        }

        // Degrees of freedom: n - 2 (for slope and intercept)
        return Math.sqrt(sumSquaredResiduals / (n - 2));
    }

    /**
     * Determines if the forecast should be flagged as low-confidence.
     * Checks if the rolling 3-month accuracy (predicted vs actual) is below 70%.
     *
     * Accuracy = 1 - |actual - predicted| / actual, averaged over last 3 months.
     */
    private boolean isLowConfidence(double[] spendValues, double slope, double intercept,
                                    double[] seasonalIndices, List<YearMonth> sortedMonths) {
        int n = spendValues.length;
        if (n < MINIMUM_HISTORY_MONTHS) return false;

        // Calculate accuracy for the last 3 months
        int startIdx = n - 3;
        double totalAccuracy = 0;
        int validMonths = 0;

        for (int i = startIdx; i < n; i++) {
            double actual = spendValues[i];
            if (actual <= 0) continue;

            int monthOfYear = sortedMonths.get(i).getMonthValue() - 1;
            double predicted = (slope * i + intercept) * seasonalIndices[monthOfYear];
            double accuracy = 1.0 - Math.abs(actual - predicted) / actual;
            accuracy = Math.max(0, accuracy); // clamp to [0, 1]
            totalAccuracy += accuracy;
            validMonths++;
        }

        if (validMonths == 0) return false;

        double averageAccuracy = totalAccuracy / validMonths;
        return averageAccuracy < LOW_CONFIDENCE_THRESHOLD;
    }
}
