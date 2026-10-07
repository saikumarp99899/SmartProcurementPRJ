# Requirements Document

## Introduction

SmartProcure AI is a comprehensive procurement management platform built with a Java 21 / Spring Boot 3.x backend and a React + Vite + Tailwind CSS frontend. This requirements document covers three major enhancement areas: (1) a modern redesign of the requisition creation and detail pages to match enterprise procurement software standards, (2) a global search bar with cross-entity search and admin proxy login capabilities, and (3) additional AI-powered features beyond the existing spend analysis — including smart vendor recommendations, anomaly detection, and predictive analytics.

## Glossary

- **System**: The SmartProcure AI application (frontend and backend combined)
- **Frontend**: The React + Vite + Tailwind CSS client application
- **Backend**: The Java 21 / Spring Boot 3.x server application
- **Global_Search_Bar**: A persistent search input positioned at the top center of the application header
- **Search_Service**: The backend service responsible for cross-entity search operations
- **Proxy_Login_Service**: The backend service that enables admin users to impersonate other users
- **Requisition_Page**: The frontend pages for creating, viewing, and listing requisitions
- **AI_Service**: The backend service providing AI-powered analytics and recommendations
- **Vendor_Recommendation_Engine**: The AI component that suggests optimal vendors for requisition items
- **Anomaly_Detection_Engine**: The AI component that identifies unusual patterns in procurement data
- **Predictive_Analytics_Engine**: The AI component that forecasts procurement trends and budget needs
- **ADMIN**: Role with full system access, including proxy login capability
- **BUYER**: Role that creates requisitions and purchase orders
- **APPROVER**: Role that approves or rejects requisitions
- **Requisition**: A procurement request raised by a BUYER
- **PurchaseOrder**: A formal order issued to a vendor after approval
- **Proxy_Session**: A temporary session where an ADMIN operates the system as another user

## Requirements

### Requirement 1: Requisition Creation Page Redesign

**User Story:** As a BUYER, I want a modern, professional requisition creation form that follows enterprise procurement UI standards, so that I can create requisitions efficiently with clear visual guidance.

#### Acceptance Criteria

1. THE Requisition_Page SHALL display a multi-section form layout with header information, line items, and summary sections each rendered as separate card containers with visible borders and spacing between them.
2. THE Requisition_Page SHALL display a sticky summary panel fixed to the viewport bottom (mobile) or top of the visible area (desktop/tablet), showing the running total amount, line item count, and submission status, remaining visible as the user scrolls through line items.
3. WHEN a BUYER adds a line item, THE Requisition_Page SHALL insert the new row with a fade-in transition lasting between 200 and 400 milliseconds and auto-focus the item name field of the newly added row.
4. WHEN a BUYER moves focus away from a required input field that contains invalid or empty content, THE Requisition_Page SHALL display an inline validation error message directly below that input field within 200 milliseconds of the blur event.
5. WHEN a BUYER submits the form with validation errors, THE Requisition_Page SHALL scroll to and highlight the first invalid field with a visible border color change.
6. THE Requisition_Page SHALL display a stepper indicator showing form completion status across three sections (Header, Items, Review), with each section marked as complete when all its required fields contain valid values.
7. WHEN a BUYER enters a unit price or quantity, THE Requisition_Page SHALL recalculate and display the line total (quantity multiplied by unit price) and the overall requisition total (sum of all line totals) within 100 milliseconds.
8. THE Requisition_Page SHALL render responsive layouts that adapt between desktop viewports (1024px and above, side-by-side panels), tablet viewports (768px to 1023px, stacked cards), and mobile viewports (below 768px, single-column).
9. WHEN a BUYER clicks the Cancel button after making changes to any form field, THE Requisition_Page SHALL display a confirmation dialog with options to discard changes and navigate away or to remain on the form.
10. WHILE the requisition form has unsaved changes, THE Requisition_Page SHALL persist form data to local storage every 30 seconds, storing a maximum of 1 MB per draft.
11. IF the Requisition_Page fails to persist draft data to local storage due to storage quota limits, THEN THE Requisition_Page SHALL display a non-blocking warning message indicating that auto-save is unavailable.
12. THE Requisition_Page SHALL allow a BUYER to add a maximum of 50 line items per requisition, disabling the add-item control when the limit is reached.

### Requirement 2: Requisition Detail Page Redesign

**User Story:** As a user, I want a modern, information-rich requisition detail view that provides clear status visibility and action context, so that I can understand requisition state at a glance.

#### Acceptance Criteria

1. THE Requisition_Page SHALL display a header section containing the requisition number, status badge, requester name, creation date, and total amount grouped within a bordered card component that is the first visible element below the navigation.
2. THE Requisition_Page SHALL display a horizontal workflow progress tracker showing all lifecycle stages (Draft, Submitted, Approved, PO Created, Completed) with the current stage visually differentiated by a distinct background color and a ring indicator, and all completed stages marked with a success color fill.
3. WHEN the requisition status is REJECTED, THE Requisition_Page SHALL display the rejection reason and rejector name in a banner with a contrasting background color distinct from the page background, positioned between the workflow tracker and the line items section.
4. THE Requisition_Page SHALL display line items in a data table with alternating row backgrounds, columns sortable by item name, quantity, unit price, and line total, and a footer row displaying the sum of all line totals.
5. IF the requisition has been submitted and at least one approval action has been recorded, THEN THE Requisition_Page SHALL display the approval history section as a vertical timeline with approver name, action taken (Approved, Rejected, or Pending), timestamp, and comments for each step.
6. IF the requisition has been submitted and no approval actions have been recorded, THEN THE Requisition_Page SHALL display a message indicating that the approval process has not yet started.
7. WHEN an ADMIN or BUYER views a requisition with status APPROVED, THE Requisition_Page SHALL display a "Create Purchase Order" button with minimum dimensions of 36px height, positioned in the page actions area.
8. THE Requisition_Page SHALL display department name, cost center name, total amount, and item count in a metadata grid section.
9. WHEN a user clicks the print button, THE Requisition_Page SHALL render a print layout that includes the header information, line items table, and approval history without navigation or action elements, formatted to fit standard A4 or Letter page dimensions.
10. WHEN the requisition data is being fetched, THE Requisition_Page SHALL display a loading indicator until the data is fully rendered, with the page content appearing within 3 seconds of navigation under normal network conditions.

### Requirement 3: Global Search Bar

**User Story:** As a user, I want a global search bar at the top center of the application, so that I can find requisitions, purchase orders, items, and vendors from anywhere in the application.

#### Acceptance Criteria

1. THE Global_Search_Bar SHALL be positioned at the top center of the application header and remain visible on all authenticated pages.
2. WHEN a user types at least 2 characters and pauses typing for 300 milliseconds (debounce), THE Global_Search_Bar SHALL display search results grouped by entity type (Requisitions, Purchase Orders, Items, Vendors) within 500 milliseconds of the debounced input.
3. THE Search_Service SHALL perform case-insensitive substring matching (matching any contiguous portion of the field value) across requisition numbers, requisition descriptions, purchase order numbers, item names, vendor names, and vendor codes.
4. WHEN a user clicks a search result, THE Frontend SHALL navigate to the respective detail page for that entity.
5. THE Global_Search_Bar SHALL support keyboard navigation with arrow keys to move between results, Enter to select the highlighted result, and Escape to close the dropdown.
6. WHEN a user presses Ctrl+K or Cmd+K, THE Frontend SHALL focus the Global_Search_Bar and select any existing text in the input field.
7. THE Global_Search_Bar SHALL display a maximum of 5 results per entity category in the dropdown, with a "View all" link for each category that navigates to a full search results page filtered by that entity type.
8. WHEN the search returns no results, THE Global_Search_Bar SHALL display a "No results found" message with a suggestion to refine the search query.
9. THE Search_Service SHALL return results filtered by the authenticated user's role-based permissions so that users only see entities they are authorized to access.
10. WHEN a GET /api/search?q={query} request is received, THE Search_Service SHALL search across requisitions, purchase orders, vendors, and items and return grouped results, accepting a query parameter between 2 and 100 characters in length.
11. WHEN a user clicks outside the search dropdown or navigates to another page, THE Global_Search_Bar SHALL close the results dropdown and retain the current query text in the input field.
12. IF the Search_Service returns an error or fails to respond within 5 seconds, THEN THE Global_Search_Bar SHALL display an error message indicating the search is temporarily unavailable and allow the user to retry.
13. IF a GET /api/search?q={query} request is received with a query shorter than 2 characters or longer than 100 characters, THEN THE Search_Service SHALL return a validation error indicating the query length is outside the accepted range.

### Requirement 4: Proxy Login (Admin Impersonation)

**User Story:** As an ADMIN, I want to log in as another user (proxy login), so that I can troubleshoot issues, verify permissions, and assist users without requiring their credentials.

#### Acceptance Criteria

1. WHEN an ADMIN types "login as" or "@" followed by at least 1 character of a username in the Global_Search_Bar, THE Frontend SHALL display a list of up to 10 matching active, non-ADMIN users available for proxy login.
2. WHEN an ADMIN selects a user for proxy login, THE Proxy_Login_Service SHALL create a Proxy_Session and issue a temporary token scoped to the target user's permissions.
3. WHILE a Proxy_Session is active, THE Frontend SHALL display a fixed-position banner at the top of the viewport indicating proxy mode with the impersonated user's name and a button to end the session, remaining visible during scrolling and on all pages.
4. WHILE a Proxy_Session is active, THE System SHALL record all actions with both the ADMIN's identity and the impersonated user's identity in the audit log.
5. WHEN an ADMIN clicks "End Proxy Session," THE Proxy_Login_Service SHALL terminate the Proxy_Session and restore the ADMIN's original session.
6. IF a non-ADMIN user attempts to initiate a proxy login, THEN THE System SHALL reject the request with an access-denied error and log the unauthorized attempt including the user's identity and timestamp.
7. THE Proxy_Login_Service SHALL limit Proxy_Session duration to a maximum of 60 minutes, after which the session SHALL auto-terminate and the Frontend SHALL restore the ADMIN's original session and display a notification indicating the proxy session has expired.
8. WHEN a POST /api/auth/proxy-login request is received with a valid target userId from an ADMIN, THE Proxy_Login_Service SHALL return a proxy token and the target user's profile.
9. IF a POST /api/auth/proxy-login request is received with a target userId that does not exist, belongs to an ADMIN, or belongs to a disabled or inactive user, THEN THE Proxy_Login_Service SHALL reject the request with an error message indicating the target user is not available for proxy login.
10. IF an ADMIN attempts to initiate a proxy login while already in an active Proxy_Session, THEN THE Proxy_Login_Service SHALL reject the request with an error message indicating nested proxy sessions are not permitted.

### Requirement 5: AI-Powered Vendor Recommendations

**User Story:** As a BUYER, I want the system to recommend optimal vendors when creating a requisition, so that I can select vendors based on historical performance, pricing, and delivery reliability.

#### Acceptance Criteria

1. WHEN a BUYER enters at least 3 characters in the item name field of the requisition creation form, THE Vendor_Recommendation_Engine SHALL suggest up to 5 vendors that have previously supplied similar items within 2 seconds.
2. THE Vendor_Recommendation_Engine SHALL rank vendor suggestions based on a composite score ranging from 0 to 100, derived from historical pricing competitiveness, delivery timeliness, and quality ratings.
3. WHEN a GET /api/ai/vendor-recommendations?itemName={name} request is received, THE AI_Service SHALL return a list of up to 5 recommended vendors, each including a confidence score between 0 and 100, and a textual reasoning summary.
4. THE Vendor_Recommendation_Engine SHALL display each recommendation with the vendor name, average price for similar items, delivery performance percentage (0 to 100), and the recommendation confidence score.
5. WHEN a BUYER selects a recommended vendor, THE Requisition_Page SHALL auto-populate the vendor name into the line item's vendor field.
6. IF the AI_Service returns no matching vendors for the entered item name, THEN THE Vendor_Recommendation_Engine SHALL display a message indicating that no vendor recommendations are available for the given item.
7. IF the AI_Service is unavailable or returns an error, THEN THE Vendor_Recommendation_Engine SHALL display a message indicating that recommendations are temporarily unavailable and SHALL allow the BUYER to continue creating the requisition without recommendations.

### Requirement 6: AI Anomaly Detection

**User Story:** As an ADMIN, I want the system to detect anomalies in procurement patterns, so that I can identify potential fraud, waste, or policy violations early.

#### Acceptance Criteria

1. THE Anomaly_Detection_Engine SHALL analyze requisition data for price anomalies by comparing item unit prices against historical averages for the same item category.
2. WHEN the Anomaly_Detection_Engine detects a unit price exceeding 2 standard deviations above the historical mean for the same item category, THE AI_Service SHALL flag the requisition line item as a price anomaly.
3. THE Anomaly_Detection_Engine SHALL detect split ordering anomalies when a single requester creates 3 or more requisitions within a 7-day window where each requisition total is below the approval threshold and the combined total exceeds the threshold.
4. THE Anomaly_Detection_Engine SHALL detect vendor concentration anomalies when more than 60 percent of spend within a category is directed to a single vendor over a rolling 90-day window.
5. WHEN a GET /api/ai/anomalies request is received by an ADMIN, THE AI_Service SHALL return a list of detected anomalies with severity level (LOW, MEDIUM, or HIGH), description, affected entities, and recommended actions.
6. THE Frontend SHALL display anomalies on a dedicated AI Anomaly Detection page accessible from the sidebar navigation to ADMIN users.
7. THE AI_Service SHALL assign severity HIGH when financial impact exceeds 10,000 USD or deviation exceeds 4 standard deviations, MEDIUM when financial impact is between 2,000 and 10,000 USD or deviation is between 2 and 4 standard deviations, and LOW for all other detected anomalies.
8. IF the Anomaly_Detection_Engine has fewer than 5 historical data points for a given item category, THEN THE Anomaly_Detection_Engine SHALL skip anomaly analysis for that category and not flag any items.
9. IF a non-ADMIN user attempts to access the GET /api/ai/anomalies endpoint, THEN THE System SHALL reject the request with HTTP 403.

### Requirement 7: AI Predictive Analytics

**User Story:** As an ADMIN or BUYER, I want predictive analytics for procurement planning, so that I can forecast budget needs and anticipate demand patterns.

#### Acceptance Criteria

1. WHEN a GET /api/ai/predictions/spend-forecast request is received from an ADMIN or BUYER, THE Predictive_Analytics_Engine SHALL return monthly spend forecasts for the next 6 months based on historical trends, including for each month the predicted spend amount and confidence interval bounds (lower and upper) representing an 80 percent confidence level.
2. WHEN a GET /api/ai/predictions/demand request is received from an ADMIN or BUYER, THE Predictive_Analytics_Engine SHALL return predicted demand for the top 10 item categories ranked by historical spend volume, with forecasts for the next 6 months based on seasonal patterns and historical ordering frequency.
3. THE Frontend SHALL display predictive analytics on a dedicated page with line charts showing at least 6 months of historical data alongside forecasted trends, with confidence interval bounds rendered as a shaded band around the forecast line.
4. WHEN new purchase order data is recorded, THE Predictive_Analytics_Engine SHALL incorporate the new data into subsequent prediction calculations so that the next forecast request reflects the latest procurement activity.
5. WHEN forecast accuracy for a category drops below 70 percent measured against actual spend over the most recent 3-month rolling window, THE Predictive_Analytics_Engine SHALL flag the forecast as low-confidence in the response.
6. IF the Predictive_Analytics_Engine has fewer than 3 months of historical data for a given category, THEN THE Predictive_Analytics_Engine SHALL omit the forecast for that category and return an indication of insufficient data.
7. IF a user without the ADMIN or BUYER role attempts to access a predictions endpoint, THEN THE System SHALL reject the request with HTTP 403.

### Requirement 8: AI-Powered Smart Requisition Assistance

**User Story:** As a BUYER, I want AI assistance while creating requisitions, so that I can fill out forms faster and reduce errors.

#### Acceptance Criteria

1. WHEN a BUYER has typed at least 2 characters in the item description field, THE AI_Service SHALL suggest item names from the historical catalog with auto-complete functionality within 300 milliseconds.
2. WHEN a BUYER adds an item to the requisition, THE AI_Service SHALL suggest a unit price based on the median of the last 10 purchases of items with the same name or items sharing the same item category.
3. WHEN a GET /api/ai/item-suggestions?prefix={text} request is received with a prefix of at least 2 characters, THE AI_Service SHALL return up to 10 item names from historical procurement data that match the prefix using case-insensitive prefix matching, within 500 milliseconds.
4. WHEN a GET /api/ai/price-suggestion?itemName={name} request is received, THE AI_Service SHALL return the suggested unit price, the price range (min/max from history), and the number of historical data points used, within 500 milliseconds.
5. IF the AI_Service has fewer than 3 historical data points for a given item, THEN THE AI_Service SHALL omit the price suggestion and return an indication of insufficient data.
6. IF the AI_Service is unavailable or returns an error when a suggestion is requested, THEN THE Frontend SHALL allow the BUYER to continue filling out the requisition form manually without interruption and SHALL display a non-blocking notification indicating that AI suggestions are temporarily unavailable.
7. IF a GET /api/ai/item-suggestions?prefix={text} request is received with a prefix shorter than 2 characters, THEN THE AI_Service SHALL return an empty result set without performing a search.
