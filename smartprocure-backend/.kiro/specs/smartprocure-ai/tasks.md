# Implementation Plan: SmartProcure AI

## Overview

This plan implements eight major enhancements to the SmartProcure procurement platform across backend (Java 21 / Spring Boot 3.x) and frontend (React + Vite + Tailwind CSS). Tasks are organized to build foundational components first, then layer AI features and UI redesigns on top, finishing with integration wiring.

## Tasks

- [x] 1. Backend foundation — Data models, DTOs, and repository extensions
  - [x] 1.1 Create ProxySession entity and proxy_sessions database migration
    - Create `ProxySession.java` entity with fields: id, admin, targetUser, token, startedAt, expiresAt, endedAt, status
    - Create `ProxySessionStatus` enum (ACTIVE, EXPIRED, ENDED)
    - Add SQL migration script for `proxy_sessions` table with foreign keys to `users`
    - _Requirements: 4.2, 4.7_

  - [x] 1.2 Add new AuditAction enum values for proxy login
    - Add PROXY_LOGIN_START, PROXY_LOGIN_END, PROXY_LOGIN_EXPIRED, PROXY_LOGIN_DENIED to existing `AuditAction` enum
    - _Requirements: 4.4, 4.6_

  - [x] 1.3 Create DTO records for Search, Proxy, and AI responses
    - Create `SearchResponse`, `SearchResult` records
    - Create `ProxyLoginRequest`, `ProxyLoginResponse` records
    - Create `VendorRecommendationResponse` record
    - Create `AnomalyResponse`, `AffectedEntity` records
    - Create `SpendForecastResponse`, `MonthlyForecast`, `HistoricalDataPoint` records
    - Create `DemandForecastResponse`, `CategoryDemandForecast`, `MonthlyDemand` records
    - Create `PriceSuggestionResponse` record
    - _Requirements: 3.10, 4.8, 5.3, 6.5, 7.1, 7.2, 8.4_

  - [x] 1.4 Create and extend repository interfaces
    - Create `ProxySessionRepository` with methods: findByAdminIdAndStatus, findByTokenAndStatus, findByStatusAndExpiresAtBefore
    - Extend or create `PurchaseOrderItemRepository` with queries: findByItemNameContainingIgnoreCase, findItemNamesByPrefix, findByItemNameIgnoreCase
    - Extend or create `RequisitionItemRepository` with query: findItemNamesByPrefix
    - _Requirements: 4.2, 5.1, 8.1, 8.2_

- [x] 2. Backend — Search Service
  - [x] 2.1 Implement SearchService with cross-entity search
    - Create `SearchService` interface and `SearchServiceImpl`
    - Implement case-insensitive LIKE queries across requisitions (number, description), purchase orders (number), vendors (name, code), and items (name)
    - Apply role-based filtering: BUYER sees own requisitions + all POs/vendors; APPROVER sees assigned requisitions; ADMIN sees all
    - Limit results to 5 per category
    - Validate query length (2-100 chars)
    - _Requirements: 3.3, 3.7, 3.9, 3.10, 3.13_

  - [x] 2.2 Create SearchController REST endpoint
    - Create `SearchController` with GET `/api/search` endpoint
    - Accept `q` query parameter with @Size(min=2, max=100) validation
    - Inject authenticated user principal for role-based filtering
    - Secure with @PreAuthorize("isAuthenticated()")
    - _Requirements: 3.10, 3.13_

  - [ ]* 2.3 Write property tests for SearchService
    - **Property 5: Search Substring Matching** — verify case-insensitive contiguous substring matching
    - **Property 6: Search Results Per-Category Limit** — verify max 5 results per category
    - **Property 7: Search Role-Based Result Filtering** — verify users only see authorized entities
    - **Property 8: Search Query Length Validation** — verify rejection of queries < 2 or > 100 chars
    - **Validates: Requirements 3.3, 3.7, 3.9, 3.13**

- [x] 3. Backend — Proxy Login Service
  - [x] 3.1 Implement ProxyLoginService
    - Create `ProxyLoginService` interface and `ProxyLoginServiceImpl`
    - Implement `initiateProxyLogin(adminId, targetUserId)`: validate target (exists, active, non-ADMIN), check no active session, create proxy_sessions record, generate proxy JWT (60 min expiry with proxy claims containing adminId and targetUserId), log PROXY_LOGIN_START
    - Implement `endProxySession(userId)`: mark session ENDED, set endedAt, log PROXY_LOGIN_END
    - Implement scheduled task to expire sessions past 60 min
    - Reject nested proxy sessions (admin already in active session)
    - _Requirements: 4.2, 4.4, 4.5, 4.6, 4.7, 4.9, 4.10_

  - [x] 3.2 Create ProxyAuthController REST endpoints
    - POST `/api/auth/proxy-login` — @PreAuthorize("hasRole('ADMIN')"), accepts ProxyLoginRequest, returns ProxyLoginResponse
    - POST `/api/auth/proxy-logout` — ends active proxy session for current user
    - _Requirements: 4.8, 4.5_

  - [x] 3.3 Extend JWT service for proxy token support
    - Add proxy claims (isProxy, adminId, targetUserId) to token generation
    - Update token validation to recognize proxy tokens and extract dual identity
    - Ensure proxy tokens expire independently (60 min)
    - _Requirements: 4.2, 4.7_

  - [ ]* 3.4 Write property tests for ProxyLoginService
    - **Property 9: Proxy Token Scoped to Target Permissions** — verify token grants exactly target user's roles
    - **Property 10: Proxy Audit Dual-Identity** — verify audit entries contain both admin and target identities
    - **Property 11: Proxy Session End Restores Admin** — verify token invalidation on session end
    - **Property 12: Invalid Proxy Login Rejection** — verify rejection for non-ADMIN, nonexistent, ADMIN-target, or inactive users
    - **Validates: Requirements 4.2, 4.4, 4.5, 4.6, 4.9**

- [x] 4. Checkpoint — Backend foundation verified
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Backend — AI Vendor Recommendation Service
  - [x] 5.1 Implement VendorRecommendationService
    - Create `VendorRecommendationService` interface and `VendorRecommendationServiceImpl`
    - Query PO items with similar names (case-insensitive LIKE)
    - Aggregate vendor metrics: average price, delivery timeliness percentage, quality rating
    - Calculate composite score (0-100) from pricing competitiveness, delivery performance, and quality
    - Sort by score descending, return top 5 with reasoning text
    - _Requirements: 5.1, 5.2, 5.3_

  - [ ]* 5.2 Write property tests for VendorRecommendationService
    - **Property 13: Vendor Recommendation Score Invariant** — verify scores 0-100, sorted descending
    - **Validates: Requirements 5.2**

- [x] 6. Backend — AI Anomaly Detection Service
  - [x] 6.1 Implement AnomalyDetectionService
    - Create `AnomalyDetectionService` interface and `AnomalyDetectionServiceImpl`
    - Implement price anomaly detection: calculate mean and stddev per item category, flag items > 2σ above mean
    - Implement split ordering detection: identify requesters with 3+ requisitions in 7-day window where each < threshold and combined > threshold
    - Implement vendor concentration detection: per category over 90-day rolling window, flag if single vendor > 60% of spend
    - Apply minimum data points guard (skip categories with < 5 historical data points)
    - Assign severity: HIGH (impact > 10,000 or > 4σ), MEDIUM (2,000-10,000 or 2-4σ), LOW (all others)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.7, 6.8_

  - [ ]* 6.2 Write property tests for AnomalyDetectionService
    - **Property 14: Price Anomaly Detection** — verify threshold-based flagging with ≥ 5 data points
    - **Property 15: Split Ordering Detection** — verify 3+ requisitions in 7-day window detection
    - **Property 16: Vendor Concentration Detection** — verify 60% threshold over 90-day window
    - **Property 17: Anomaly Severity Classification** — verify correct severity assignment based on impact/deviation
    - **Property 18: Minimum Data Points Guard** — verify no flags for categories with < 5 data points
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.7, 6.8**

- [x] 7. Backend — AI Predictive Analytics Service
  - [x] 7.1 Implement PredictiveAnalyticsService
    - Create `PredictiveAnalyticsService` interface and `PredictiveAnalyticsServiceImpl`
    - Implement `getSpendForecast()`: analyze historical PO data, compute 6-month forecast with linear trend + seasonal adjustment, calculate 80% confidence intervals (lower ≤ predicted ≤ upper)
    - Implement `getDemandForecast()`: identify top 10 categories by spend volume, forecast 6 months of demand per category
    - Apply minimum history guard (omit categories with < 3 months of data, return insufficientData flag)
    - Flag low-confidence forecasts when rolling 3-month accuracy < 70%
    - _Requirements: 7.1, 7.2, 7.4, 7.5, 7.6_

  - [ ]* 7.2 Write property tests for PredictiveAnalyticsService
    - **Property 19: Spend Forecast Confidence Interval Validity** — verify 6 entries with lower ≤ predicted ≤ upper
    - **Property 20: Low-Confidence Forecast Flagging** — verify flagging when accuracy < 70%
    - **Property 21: Insufficient History Guard** — verify omission for categories with < 3 months data
    - **Validates: Requirements 7.1, 7.5, 7.6**

- [x] 8. Backend — AI Smart Assistance Service
  - [x] 8.1 Implement SmartAssistanceService
    - Create `SmartAssistanceService` interface and `SmartAssistanceServiceImpl`
    - Implement `getItemSuggestions(prefix)`: query distinct item names from requisition and PO items using case-insensitive prefix match, return up to 10 results, return empty list for prefix < 2 chars
    - Implement `getPriceSuggestion(itemName)`: query last 10 purchases of matching item name, compute median price, min, max, data point count; return insufficientData if < 3 records
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.7_

  - [ ]* 8.2 Write property tests for SmartAssistanceService
    - **Property 23: Item Suggestion Prefix Matching** — verify prefix match, max 10 results, completeness
    - **Property 24: Price Suggestion Correctness** — verify median calculation, min/max bounds, insufficientData guard
    - **Property 25: Empty Result for Short Prefix** — verify empty list for prefix < 2 chars
    - **Validates: Requirements 8.1, 8.2, 8.3, 8.4, 8.5, 8.7**

- [x] 9. Backend — AIController endpoints
  - [x] 9.1 Create/extend AIController with all AI endpoints
    - GET `/api/ai/vendor-recommendations?itemName={name}` — @PreAuthorize BUYER or ADMIN
    - GET `/api/ai/anomalies` — @PreAuthorize ADMIN only
    - GET `/api/ai/predictions/spend-forecast` — @PreAuthorize ADMIN or BUYER
    - GET `/api/ai/predictions/demand` — @PreAuthorize ADMIN or BUYER
    - GET `/api/ai/item-suggestions?prefix={text}` — @PreAuthorize BUYER or ADMIN
    - GET `/api/ai/price-suggestion?itemName={name}` — @PreAuthorize BUYER or ADMIN
    - Wire service dependencies and handle error responses
    - _Requirements: 5.3, 6.5, 6.9, 7.1, 7.2, 7.7, 8.3, 8.4_

  - [ ]* 9.2 Write property test for role-based endpoint access control
    - **Property 22: Role-Based Endpoint Access Control** — verify HTTP 403 for unauthorized roles
    - **Validates: Requirements 6.9, 7.7**

- [x] 10. Checkpoint — All backend services verified
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Frontend — API layer and shared utilities
  - [x] 11.1 Create searchApi.js and extend authApi.js and aiApi.js
    - Create `src/api/searchApi.js` with `search(q)` method
    - Extend `authApi.js` with `proxyLogin(targetUserId)` and `proxyLogout()` methods
    - Extend `aiApi.js` with `getVendorRecommendations`, `getAnomalies`, `getSpendForecast`, `getDemandForecast`, `getItemSuggestions`, `getPriceSuggestion` methods
    - _Requirements: 3.10, 4.8, 5.3, 6.5, 7.1, 7.2, 8.3, 8.4_

  - [x] 11.2 Extend AuthContext for proxy session state
    - Add proxy session state: isProxy, proxyUser, originalToken
    - Implement `startProxySession(proxyToken, targetUser)`: store original token in localStorage, set proxy state
    - Implement `endProxySession()`: restore original token, clear proxy state
    - Handle proxy session expiry (60 min): auto-restore admin session, show notification
    - _Requirements: 4.3, 4.5, 4.7_

- [x] 12. Frontend — Global Search Bar
  - [x] 12.1 Implement GlobalSearchBar component
    - Create `src/components/GlobalSearchBar.jsx`
    - Position at top center of header, visible on all authenticated pages
    - Implement 300ms debounce on input (minimum 2 characters)
    - Display results grouped by entity type (Requisitions, Purchase Orders, Items, Vendors)
    - Limit to 5 results per category with "View all" link
    - Implement keyboard navigation: arrow keys, Enter to select, Escape to close
    - Implement Ctrl+K / Cmd+K hotkey to focus search
    - Handle click outside to close dropdown (retain query text)
    - Show "No results found" message when empty
    - Show error state with retry on API timeout (5s) or error
    - For ADMIN: detect "login as" or "@" prefix to show proxy login user list
    - _Requirements: 3.1, 3.2, 3.4, 3.5, 3.6, 3.7, 3.8, 3.11, 3.12, 4.1_

- [x] 13. Frontend — Proxy Banner
  - [x] 13.1 Implement ProxyBanner component
    - Create `src/components/ProxyBanner.jsx`
    - Fixed-position banner at top of viewport when proxy session is active
    - Display impersonated user's name and role
    - Include "End Proxy Session" button that calls `authApi.proxyLogout()` and restores admin session
    - Remain visible on all pages during scroll
    - Integrate into Layout component, conditionally rendered based on AuthContext proxy state
    - _Requirements: 4.3, 4.5_

- [x] 14. Frontend — Requisition Creation Page Redesign
  - [x] 14.1 Redesign CreateRequisition page with multi-section form
    - Rewrite `src/pages/requisitions/CreateRequisition.jsx` using react-hook-form + zod validation
    - Create sub-components: `RequisitionStepper.jsx`, `RequisitionHeader.jsx`, `LineItemsSection.jsx`, `RequisitionSummary.jsx`
    - Implement stepper with 3 sections (Header, Items, Review) — mark sections complete when all required fields valid
    - Implement sticky summary panel (bottom on mobile, top on desktop/tablet) showing total amount, item count, status
    - Implement responsive layouts: desktop (≥1024px side-by-side), tablet (768-1023px stacked), mobile (<768px single-column)
    - Line item add with fade-in transition (200-400ms) and auto-focus on item name
    - Inline validation on blur: show error below field within 200ms
    - On submit with errors: scroll to first invalid field with border highlight
    - Cancel with unsaved changes: show confirmation dialog
    - Enforce 50 line item maximum, disable add button at limit
    - Recalculate line totals and overall total within 100ms of quantity/price change
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.12_

  - [x] 14.2 Implement auto-save to localStorage
    - Save form data to localStorage every 30 seconds while form has unsaved changes
    - Limit storage to 1 MB per draft
    - On page load: detect and restore draft data from localStorage
    - Handle quota exceeded error: show non-blocking warning banner
    - _Requirements: 1.10, 1.11_

  - [ ]* 14.3 Write property tests for line total calculations
    - **Property 1: Line Total Calculation Invariant** — verify lineTotal = quantity × unitPrice, requisitionTotal = sum of lineTotals
    - **Validates: Requirements 1.7**

  - [ ]* 14.4 Write property test for auto-save round trip
    - **Property 2: Auto-Save Round Trip** — verify serialize → deserialize preserves all field values
    - **Validates: Requirements 1.10**

- [x] 15. Frontend — Requisition Detail Page Redesign
  - [x] 15.1 Redesign RequisitionDetail page
    - Rewrite `src/pages/requisitions/RequisitionDetail.jsx`
    - Create sub-components: `WorkflowTracker.jsx`, `ApprovalTimeline.jsx`, `LineItemsTable.jsx`, `RequisitionMetadata.jsx`, `PrintLayout.jsx`
    - Header card: requisition number, status badge, requester name, creation date, total amount
    - Workflow progress tracker: horizontal stages (Draft, Submitted, Approved, PO Created, Completed) — current stage with distinct bg + ring, completed with success fill
    - Rejection banner: show between tracker and items when status is REJECTED (contrasting bg, rejector name, reason)
    - Line items table: alternating row backgrounds, sortable columns (item name, quantity, unit price, line total), footer with total sum
    - Approval timeline: vertical timeline when approval actions exist; "Not yet started" message otherwise
    - Metadata grid: department, cost center, total, item count
    - "Create Purchase Order" button for ADMIN/BUYER when status is APPROVED (min 36px height)
    - Print layout: header + line items + approval history, no nav/actions, A4/Letter fit
    - Loading indicator during data fetch (content within 3s)
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10_

  - [ ]* 15.2 Write property tests for workflow tracker and sort logic
    - **Property 3: Workflow Tracker Stage Correctness** — verify stage marking based on requisition status
    - **Property 4: Line Items Table Sort Correctness** — verify sort order correctness and footer total invariance
    - **Validates: Requirements 2.2, 2.4**

- [x] 16. Frontend — Vendor Recommendations Panel
  - [x] 16.1 Implement VendorRecommendationPanel component
    - Create `src/pages/requisitions/VendorRecommendationPanel.jsx`
    - Trigger API call when item name field has ≥ 3 characters
    - Display up to 5 vendor cards with: vendor name, average price, delivery performance %, confidence score, reasoning
    - On vendor selection: auto-populate vendor field in line item
    - Show "No recommendations available" when API returns empty
    - Show "Temporarily unavailable" on API error (non-blocking)
    - Integrate into CreateRequisition page's line items section
    - _Requirements: 5.1, 5.4, 5.5, 5.6, 5.7_

- [x] 17. Frontend — AI Anomaly Detection Page
  - [x] 17.1 Implement AnomalyDetection page
    - Create `src/pages/ai/AnomalyDetection.jsx`
    - Fetch anomalies from GET `/api/ai/anomalies`
    - Display anomaly cards with: type icon/badge, severity indicator (color-coded), description, affected entities list, recommended action
    - Filter/group by anomaly type (Price, Split Order, Vendor Concentration)
    - Filter by severity level
    - Add navigation entry in sidebar for ADMIN role
    - _Requirements: 6.5, 6.6_

- [x] 18. Frontend — AI Predictive Analytics Page
  - [x] 18.1 Implement PredictiveAnalytics page
    - Create `src/pages/ai/PredictiveAnalytics.jsx`
    - Fetch spend forecast and demand forecast data
    - Render line charts with 6+ months historical data and forecast line
    - Render confidence intervals as shaded band around forecast line
    - Show "Not enough data" message for categories with insufficientData flag
    - Show low-confidence indicator for flagged forecasts
    - Add navigation entry in sidebar for ADMIN and BUYER roles
    - _Requirements: 7.1, 7.2, 7.3, 7.5, 7.6_

- [x] 19. Frontend — Smart Requisition Assistance integration
  - [x] 19.1 Integrate AI item autocomplete and price suggestions into CreateRequisition
    - In item name field: trigger `getItemSuggestions(prefix)` after 2 characters typed, show dropdown with up to 10 suggestions
    - On item selection or name entry: call `getPriceSuggestion(itemName)`, auto-populate unit price if sufficient data
    - Show price range (min-max) as helper text below unit price field
    - Handle insufficientData gracefully (no price populated, no error shown)
    - Handle API errors: show non-blocking toast, allow manual entry
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6_

- [x] 20. Frontend — Routing, sidebar, and integration wiring
  - [x] 20.1 Update App routing and Sidebar navigation
    - Add route for `/ai/anomaly-detection` → AnomalyDetection page (ADMIN only)
    - Add route for `/ai/predictive-analytics` → PredictiveAnalytics page (ADMIN, BUYER)
    - Update Sidebar to show new AI navigation entries based on user role
    - Integrate GlobalSearchBar into Header/Layout component
    - Integrate ProxyBanner into Layout component
    - Ensure all new pages are protected by appropriate role guards
    - _Requirements: 6.6, 7.3, 3.1, 4.3_

- [x] 21. Final checkpoint — Full integration verified
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- Backend services (tasks 1-10) should be completed before frontend integration (tasks 11-20)
- The frontend uses react-hook-form + zod for form validation and TanStack Query for server state
- jqwik is used for Java property-based tests; fast-check + Vitest for frontend property tests
- All AI features use rule-based/statistical analysis — no external LLM dependency

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "1.4"] },
    { "id": 1, "tasks": ["2.1", "3.1", "3.3", "11.1"] },
    { "id": 2, "tasks": ["2.2", "2.3", "3.2", "3.4", "5.1", "6.1", "7.1", "8.1", "11.2"] },
    { "id": 3, "tasks": ["5.2", "6.2", "7.2", "8.2", "9.1"] },
    { "id": 4, "tasks": ["9.2", "12.1", "13.1"] },
    { "id": 5, "tasks": ["14.1", "15.1", "17.1", "18.1"] },
    { "id": 6, "tasks": ["14.2", "14.3", "15.2", "16.1", "19.1"] },
    { "id": 7, "tasks": ["14.4", "20.1"] }
  ]
}
```
