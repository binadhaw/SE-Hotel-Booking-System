# Hotel Reservation System – Upgrade Roadmap

Source documents: `SE Proposal Report Update.pdf` (functional requirements),
`Agile framework report.pdf` (product backlog PBI-01 … PBI-24, sprint plan) and
`SE2030_Group Project Specification.pdf` (marking rubric - phases 9-12).

Each phase compiles and runs on its own. The status column is updated as phases finish.

| Phase | Theme | Backlog items | Status |
|-------|-------|---------------|--------|
| 1 | User accounts & authentication | PBI-09, PBI-10, PBI-12, PBI-15, minor fn: login/logout, password reset, profile | Done |
| 2 | Hotel & room management (ownership, approval, safe removal, hotel detail page) | PBI-13, PBI-16, PBI-17 | Done |
| 3 | Search & booking (price/facility/date search, date-based availability, multi-room, references, real coupons, emails) | PBI-01, PBI-02, PBI-05, PBI-06, PBI-18 | Done |
| 4 | Reviews, inquiries & in-app notifications | PBI-08, PBI-11, PBI-20 | Done |
| 5 | Itinerary & local experiences (attractions, versioned proposals, agent public profile) | PBI-03, PBI-07, PBI-21, PBI-22, PBI-23, PBI-24 | Done |
| 6 | Promotions (hotel-scoped offers, emailed coupons, promo alerts) | PBI-04, PBI-19 | Done |
| 7 | Admin dashboard, activity log & reports | PBI-14 | Done |
| 8 | Quality: error pages, tests, README | NFR: reliability, usability, security | Done |
| 9 | Design patterns integrated in the code (Strategy, Observer, Template Method) | Spec: "Application of minimum 1 design pattern" (5 marks) | Done |
| 10 | CRUD & input-validation audit across every module | Spec: "CRUD Operations & Input Validation" (12 marks) | Done |
| 11 | Report support: design-pattern write-up, class & ER diagrams | Spec: Design document / final report | Done |
| 12 | Final verification and test-data cleanup | Spec: "Integration & Stability" (10 marks) | Done |
| 13 | Full proposal fit: online payment, hotel staff, travel preferences, activities on bookings, data protection | Proposal gaps | Done |
| 14 | Final proposal audit: no double booking, system-performance monitoring, faster search, use case & activity diagrams, traceability | Proposal admin role + NFR performance/reliability; spec design document | Done |
| 15 | Finalisation: paginated hotel reviews, consent at sign-up, privacy notice, private payment receipts, ethical considerations | PBI-11 (T-14.2), NFR security, spec design document | Done |
| 16 | Booking & checkout redesign, stronger card validation, UI bug fixes | PBI-02, PBI-06, NFR usability & security | Done |
| 17 | Login/register redesign, range calendar, hosted payment gateway (redirect flow) | PBI-02, PBI-10, NFR usability & security | Done |

## Phase 1 – User accounts & authentication
- `AppUser` entity stored in MySQL (BCrypt passwords, role, enabled flag, security question).
- Registration for Tourist / Hotel Manager / Travel Agent. Admins are seeded, not self-registered.
- Custom login page; role-based landing page after login.
- Forgot password using the user's security question (as specified in T-03.2/T-03.3).
- Profile page: edit name, email, phone; change password.
- Admin user management: search, suspend/activate, delete.
- Public pages (home, hotel list, hotel rooms) can be browsed without logging in.
- Demo accounts (`user`, `manager`, `agent`, `admin` – passwords `<name>123`) are seeded on first start.

## Phase 2 – Hotel & room management
- Every hotel belongs to the manager who registered it; managers only see and edit their own hotels and rooms.
- Admin approve / reject (with reason) newly registered hotels.
- Removing a hotel never destroys booking records: open bookings are cancelled (refund pending when paid) and the hotel is hidden.
- Public hotel detail page (gallery, amenities, rooms, from-price).

## Phase 3 – Search & booking
- Search by location/name, price range, amenities, dates and guest count; sorting.
- Availability is date-based: a room is unavailable only for dates overlapping an approved booking.
- Book several rooms in one go (family traveller); bookings share a group reference.
- Human-readable booking reference (e.g. `BK-7F3A21C9`).
- Coupons validated against the Promotions table instead of hard-coded codes.
- Confirmation emails go to the guest's real email address.

## Phase 4 – Reviews, inquiries & notifications
- Guests review a hotel (1–5 stars) after a completed stay; one review per booking.
- Average rating shown on hotel lists and detail pages; managers reply to reviews.
- Inquiries are addressed to a specific hotel and routed to its manager; ticket numbers.
- In-app notification centre (navbar bell) for booking, hotel, inquiry, review and itinerary events.

## Phase 5 – Itinerary & local experiences
- Attractions (with distance and cost) linked to hotels; shown on the hotel page, sortable by distance or cost.
- Agents recommend attractions and attach them to itinerary proposals.
- Proposal revisions are versioned; the tourist sees the change history.
- Public agent profile page (bio, expertise, sample itineraries, featured experiences).
- Fixes: agents can no longer approve themselves via the profile form; per-agent performance metrics.

## Phase 6 – Promotions
- Promotions may be platform-wide (admin) or for one hotel (its manager).
- Managers/admins can email a coupon (value, terms, expiry) to opted-in tourists.
- Tourists opt in/out of promotional alerts on their profile.

## Phase 7 – Admin dashboard & reports
- KPIs: users by role, hotels by status, bookings by status, revenue, open inquiries.
- Charts for bookings and revenue per month.
- Activity log of important actions (approvals, removals, suspensions…).
- CSV export of bookings.

## Phase 8 – Quality
- Friendly error pages; no stack traces shown to users.
- Unit and web tests for the core rules (availability, pricing, access control).
- README with setup and demo accounts.

## Phase 9 - Design patterns
The final-presentation rubric gives full marks for at least two patterns that are implemented, integrated
across modules and justified with code references. Patterns are applied where they solve a real problem:
- **Strategy** - discount pricing: each discount source (coupon, standard discount) is a strategy; the
  pricing service picks the best one. New discount types plug in without touching booking code.
- **Observer** - domain events (booking created/approved/cancelled, hotel approved/removed, review posted...)
  are published once; independent listeners send notifications/emails and write the audit log.
- **Template Method** - hotel and room photo storage share one validation/storage algorithm in an abstract
  base class; subclasses only supply the folder and limits (removes duplicated code).
- (Framework-provided, explained in the write-up: Singleton beans and Dependency Injection, Repository.)

## Phase 10 - CRUD & validation audit
Every module offers create, read, update and delete in the UI, and every form is validated on the server
(bean validation + business rules) with messages shown next to the fields.

## Phase 11 - Report support
`docs/DESIGN_PATTERNS.md` (where / why / benefit with file references), `docs/DIAGRAMS.md` (class and ER
diagrams in Mermaid), CRUD/validation matrix.

## Phase 12 - Final verification
Full regression, visual check of key screens, and removal of the test records created while upgrading.

## Phase 13 - Full fit with the proposal
Closes the gaps found in the proposal review:
1. **Online card payment** ("book rooms with online payment", "secure payment processing", "confirms bookings instantly")
   - simulated payment gateway: card number (Luhn), expiry and CVV checks; only brand + last 4 digits stored
   - paying online confirms the booking instantly; bank-transfer receipts still work as before
2. **Hotel staff management** ("manage their property details, amenities, and staff") - full CRUD per hotel
3. **Travel preferences** ("personalized offers based on tourist preferences") - interests, preferred destination
   and budget on the profile; promotion alerts are targeted; "Recommended for you" on the home page
4. **Suggested activities alongside bookings** - nearby experiences shown for each upcoming stay
5. **Data protection** ("payments and personal data are encrypted") - AES-GCM encryption of personal fields at
   rest, optional HTTPS profile

## Phase 14 - Final proposal audit
Every proposal requirement was re-checked end to end (data injected phase by phase through the web pages
against MySQL, 231 checks) - see `docs/REQUIREMENTS_TRACEABILITY.md`. Gaps found and closed:
1. **Double booking under simultaneous requests** (reliability) - two guests paying for the same room at the same
   moment could both be confirmed. Every confirmation (card payment, manager approval, guest edit that stays
   approved) now locks the room row before re-checking for a clash. `ConcurrentBookingTest` proves only one wins.
2. **"Administrator can monitor system performance"** - new *System health* panel on the admin dashboard
   (response times, error rate, uptime, database, memory, slowest pages) fed by `RequestMetricsFilter`.
3. **"Fast search results even during high traffic"** - search loaded ratings with two queries per hotel and
   rooms one hotel at a time. Ratings now come from one grouped query and lazy collections are batch-loaded.
   With 329 hotels and 20 concurrent users: 16 -> 65 requests/s, median 1386 -> 266 ms.
4. **Design document** - use case diagrams and one activity diagram per major function added to `docs/DIAGRAMS.md`.

## Phase 15 - Finalisation
Last pass over the proposal, the Agile backlog task lists and the design-document structure:
1. **Paginated reviews** (backlog task T-14.2 "fetch and paginate reviews per hotel") - the hotel page shows
   5 reviews per page; the average and star distribution still count every visible review.
2. **User consent** - sign-up requires accepting the new privacy notice (`/privacy`, also linked in the footer).
   Promotional emails are now opt-in: the box is unticked at sign-up (previously every new tourist was opted in).
3. **Ethical considerations** - `docs/ETHICAL_CONSIDERATIONS.md` for the design document (privacy, consent,
   fairness of reviews, accountability, accessibility, upload safety, honest list of remaining limitations).
4. **Private payment receipts** - receipts were served as static files to any logged-in user with the link.
   They are now served by `/bookings/{id}/receipt-file`, which only allows the guest, that hotel's manager and
   admins; the direct `/uploads/receipts/` path is blocked. Managers get a "View receipt" link on the bookings list.

## Phase 16 - Booking & checkout redesign
1. **Reservation page** - photo hero with a live 4-step progress tracker, date range with a nights counter,
   +/- guest steppers, photo room tiles, live availability (`/bookings/new/{hotelId}/availability`) that greys out
   rooms booked for the chosen nights, and a sticky summary with an animated total.
   Bugs fixed: the page could open or be submitted with check-out on the same day as check-in (now defaults to
   tonight -> tomorrow, check-out follows check-in, inline error, button disabled); a failed submission lost the
   guest's rooms and dates; stays over 30 nights or more than a year ahead are refused.
2. **Checkout** - 3D card preview that flips to show the CVV and takes the card brand's colours, accepted-brand
   highlighting, one-tap demo cards, processing overlay. Validation as you type and on the server
   (`CardDetails.problem`): name without digits or double spaces, digits-only numbers, exact length per brand
   (Visa 13/16/19, Mastercard 16, Amex 15), Luhn, repeated-digit numbers refused, MM/YY or MM/YYYY expiry,
   brand-specific CVV; the field at fault is highlighted after a server-side error. `PaymentAttemptLimiter` pauses
   checkout after 5 failed attempts in 15 minutes. More demo decline cards and a card limit in the gateway.
3. **Other fixes** - the hotels page showed the internal role tag (`[ROLE_ADMIN]`); it now shows the user's name
   and "Administrator"/"Hotel manager"/... Admin dashboard: plurals ("1 tourist", "2 hotels to approve") and the
   missing "MB" on the memory figure.

## Phase 17 - Sign-in pages, date calendar and hosted payment page
1. **Login and register** - full-width emerald scene with "Ayubowan" greeting, postcard photos (Nine Arch Bridge,
   the south coast...) and destination chips; compact form card with show-password, Caps Lock warning, password
   strength meter and match hint. Photos are shown at or below their real size (all source images are at most
   736 px wide), so nothing is stretched or zoomed any more - that was the cause of the blurry images.
2. **Reservation page** - split hero (hotel story + framed photos at natural size) instead of a stretched,
   zooming background photo. The native date inputs were replaced by a range calendar: on load the check-out
   picker allowed days on or before check-in, and only the small icon opened the picker. The calendar picks
   check-in then check-out, previews the range, blocks past days, stays over 30 nights and check-ins more than a
   year ahead, and offers Tonight / This weekend / One week shortcuts.
3. **AuraPay hosted payment page** - card details are no longer posted to the hotel site. The review page opens a
   payment session (`HostedCheckoutService`) and redirects to `/gateway/{id}`, a separately branded page with a
   15-minute session timer, card step, 3-D Secure style one-time code (3 tries) and "Cancel and return". The card
   is charged through the existing `PaymentService` (room locks, attempt limiter, demo decline cards), then the
   guest is redirected to `/payments/return/{id}`, which reads the outcome from the session on the server.
