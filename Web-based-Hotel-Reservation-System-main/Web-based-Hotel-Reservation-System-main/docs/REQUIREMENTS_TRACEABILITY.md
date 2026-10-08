# Requirements Traceability - Proposal to System

Every requirement in `SE Proposal Report Update.pdf`, where it is implemented, and how it was verified.
Use this for the final report section *"how the system meets the defined requirements"* and for the viva.

Verification legend:
- **JUnit**: automated test in `src/test/java` (`mvnw test`, H2 in-memory database)
- **E2E**: end-to-end run on 2026-10-06 against MySQL. Data was injected phase by phase through the real web
  pages (registration -> hotels -> rooms -> promotions -> search -> bookings and payments -> reviews ->
  inquiries -> agents -> admin -> security). 231 checks, all passing. Results were checked both on the page
  and in the database.

## Major functions

### 1. Hotel Management (Administrator, Hotel Manager)

| Proposal statement | Implementation | Verified by |
|---|---|---|
| Admin approves and monitors registered hotels | `HotelController.approveHotel/rejectHotel`, `HotelService.approve/reject`; pending list `/hotels?status=PENDING` | E2E: new hotel is PENDING and hidden; approve -> public; reject stores reason; manager cannot approve own hotel |
| Managers register their property | `HotelController.saveHotel` (owner = logged-in manager; forged status/owner fields ignored) | E2E: register, validation (no photo, blank name, non-image upload), tourist gets 403 |
| Manage property details and amenities | `HotelController.updateHotel` (ownership check), amenity list, photo gallery | E2E: owner edit works, other manager's edit refused |
| Manage staff | `StaffController`, `StaffService` (full CRUD, role/shift lists) | E2E: add, update, delete, invalid role refused |
| Admin keeps oversight | Safe removal `HotelService.removeHotel`: open bookings cancelled or refunded, guests notified, history kept | E2E: 5 open bookings closed, history kept, hotel not bookable |

### 2. Room Management (Hotel Manager)

| Proposal statement | Implementation | Verified by |
|---|---|---|
| Add, update, remove rooms | `RoomController`; `RoomService.deleteRoomSafely` (rooms with history become unavailable instead) | E2E: create, update, delete; another manager's hotel refused |
| Set pricing | Room price with validation (above 0, up to 100000) | E2E: price 0 / negative / 25 guests / duplicate number refused |
| Real-time availability | Date-based availability `RoomService.isRoomFree`, availability toggle, early room release | JUnit `WebFlowIntegrationTest.roomIsOnlyBlockedForOverlappingNights`; E2E back-to-back stays allowed |

### 3. Booking Management (Tourist, Hotel Manager)

| Proposal statement | Implementation | Verified by |
|---|---|---|
| Search hotels | `HotelSearchService` (location, price, amenities, dates, guests, sorting) | E2E: each filter, injection-style input safe |
| Real-time room availability | Search hides rooms booked for the chosen nights | E2E: date + guest search |
| Pay on a hosted payment page (redirect flow) | `PaymentController.start` -> `GatewayController` (AuraPay page: card, one-time code) -> `PaymentController.returned` reads the result from `HostedCheckoutService` on the server; sessions are per user and expire after 15 minutes | JUnit `WebFlowIntegrationTest.hostedGatewayRedirectFlowConfirmsTheBooking`, `gatewaySessionsArePrivateAndCanBeCancelled`, `declinedCardReturnsToReviewWithTheBankMessage`, `CheckoutValidationTest.hostedPaymentSessionsExpireAndStayWithTheirOwner` |
| Book rooms with online payment, instant confirmation | `PaymentController`, `PaymentService.pay` (Luhn/expiry/CVV, gateway, booking APPROVED at once) | E2E: declined, expired and invalid cards refused, valid card approves instantly, no double charge |
| No room is ever sold twice | Room row lock `RoomRepository.lockAllById` before every confirmation (card payment, manager approval, guest edit) | JUnit `ConcurrentBookingTest` (8 simultaneous payments / approvals / edits -> exactly 1 wins); E2E on MySQL |
| Managers view, approve and manage reservations | `BookingController` approve / reject / cancel / release / refund (own hotels only) | E2E: full refund lifecycle, other manager refused |
| Confirmation sent by email | `EmailService.sendBookingReceipt` via `NotificationListener` (Observer) | E2E: confirmation email logged for the guest |

### 4. Itinerary & Local Experience (Tourist, Travel Agent)

| Proposal statement | Implementation | Verified by |
|---|---|---|
| Agents create customised itineraries | `AgentService.submitProposal`, versioned `ItineraryRevision` | E2E: v1 -> change request -> v2 -> accept, 2 revisions stored |
| Agents recommend attractions and experiences | `AttractionController`, attractions attached to proposals, public agent profile | E2E: attraction CRUD, public profile visible |
| Tourists see activities alongside bookings | Nearby attractions on "My bookings" for each upcoming stay | E2E: attraction listed next to the booking |
| Tourists request or book an agent | `/agents/explore`, `AgentController.createVacationRequest` (verified agents only) | E2E: unverified agent refused, validation, withdraw rules |

### 5. Promotion Management (Hotel Manager, Administrator)

| Proposal statement | Implementation | Verified by |
|---|---|---|
| Targeted promotions and discounts | `PromotionController`; hotel-scoped or platform-wide; Strategy pattern `pricing/` picks the best discount | JUnit `DesignPatternsTest`; E2E coupon 15% = 170 of 200, better discount wins, never combined |
| Automated notifications of relevant deals | `PromotionService.alertSubscribers` - opted-in tourists whose preferred destination matches | E2E: opted-in tourist alerted |
| Email coupons with value, terms and expiry | `PromotionService.emailCoupon` | E2E: coupon email sent to the opted-in tourist |

### 6. Inquiry System & Feedback (Tourist, Hotel Manager)

| Proposal statement | Implementation | Verified by |
|---|---|---|
| Inquiries to hotels as support tickets | `InquiryController`, ticket numbers, routed to the hotel's manager | E2E: ticket issued, edit, withdraw, delete |
| Priority for urgent matters | Priority LOW / MEDIUM / HIGH, filter on the inbox | E2E: HIGH priority stored and shown |
| Managers respond | `InquiryController.respondInquiry` (own hotels only) | E2E: response reaches the tourist; other manager refused |
| Reviews and ratings after the stay | `ReviewService` (completed stay, one per booking, 1-5), manager reply, admin moderation | JUnit `BookingRulesTest`; E2E: all rules plus XSS escaped |
| Visitors read ratings and reviews (PBI-11, T-14.2 "fetch and paginate reviews") | Hotel page shows average, star distribution and reviews 5 per page (`HotelController.showHotel`, `fragments/reviews.html`) | JUnit `WebFlowIntegrationTest.hotelReviewsArePaginated` |

## Minor functions

| Proposal statement | Implementation | Verified by |
|---|---|---|
| Login and logout | Spring Security form login, role-based landing page | E2E: demo logins, wrong password, logout ends session |
| Password reset | Security question flow `AuthController` | E2E: wrong answer refused, correct answer resets |
| Edit profile (name, password, contact) | `ProfileController` | E2E: profile, preferences, password change |
| Consent at sign-up (privacy notice, opt-in promotions) | `RegistrationForm.acceptPrivacy` (`@AssertTrue`), opt-in `promoAlerts`, public `/privacy` page | JUnit `WebFlowIntegrationTest.registrationNeedsPrivacyConsent` |
| Notifications and alerts | `NotificationService`, navbar bell, emails (Observer listeners) | E2E: notifications created, read-all, delete, clear |

## Stakeholder capabilities (proposal "Major Stakeholders")

| Stakeholder | Capability | Implementation |
|---|---|---|
| Administrator | Manage users and hotels system-wide | `/admin/users` (search, suspend, activate, delete), hotel approval/removal |
| Administrator | **Monitor system performance** | **System health panel** on `/admin/dashboard`: `RequestMetricsFilter` times every page, `SystemHealthService` reports average and 95th-percentile response time, error rate, uptime, database status and memory, plus the slowest pages; status turns *Degraded* when p95 > 1 s or errors > 2 % (JUnit `SystemHealthServiceTest`) |
| Administrator | Generate reports | KPI dashboard and charts, activity log, `/reports/bookings.csv` |

## Non-functional requirements

| NFR | How it is met | Evidence |
|---|---|---|
| Security - payments and personal data encrypted | AES-256-GCM `FieldEncryptor` for phone numbers, card holder names and contact details; BCrypt passwords; only card brand + last 4 digits stored; CSRF protection; role-based access; optional HTTPS profile | E2E: phone and card holder stored as `enc:v1:...`, CSRF-less POST -> 403, receipts not public, `X-Frame-Options: DENY`, `nosniff` |
| Performance - fast search under high traffic | Ratings for all hotels in one query (`RatingProvider.allHotelRatings`); rooms and photos batch-loaded (`hibernate.default_batch_fetch_size=50`) | Load test with 329 hotels and 20 concurrent users: 16 -> 65 requests/s, median 1386 -> 266 ms, single search 721 -> 95 ms, 0 errors |
| Scalability | Query count per search no longer grows with one query per hotel; stateless pages; MySQL | Same load test |
| Security - payment receipts private | `BookingController.receiptFile` checks guest / hotel manager / admin; `/uploads/receipts/**` denied in `SecurityConfig` | JUnit `WebFlowIntegrationTest.receiptsAreOnlyVisibleToGuestHotelManagerAndAdmin` |
| Security - user consent and privacy (design document: ethical considerations) | Privacy notice accepted at sign-up; promotional emails opt-in; see `docs/ETHICAL_CONSIDERATIONS.md` | JUnit `WebFlowIntegrationTest.registrationNeedsPrivacyConsent` |
| Usability | Consistent Bootstrap layout, inline validation messages, friendly error pages | Manual review |
| Compatibility | Responsive Bootstrap 5 pages, standard HTML/CSS (the proposal scope is desktop browsers) | Manual check |
| Reliability | Transactions around every booking state change; room locks prevent double booking; no stack traces shown to users | JUnit `ConcurrentBookingTest`; E2E: zero server errors in the log |

## Limitations (as stated in the proposal) - still true

Internet access required; Sri Lanka only; single currency (USD) and language; hotels go live only after
admin approval; the payment gateway is a built-in simulator (no real money moves).
