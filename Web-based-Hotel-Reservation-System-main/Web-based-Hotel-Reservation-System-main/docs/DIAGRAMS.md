# Diagrams

Diagrams are written in [Mermaid](https://mermaid.js.org). They render on GitHub, in IntelliJ (Mermaid
plugin) and at https://mermaid.live - paste a block there and export a PNG/SVG for the report.
They are generated from the current code, so they match what the system actually does.

---

## 1. ER diagram (database)

Tables are created by Hibernate from the `model/` entities. Solid lines are real foreign keys.
Users are linked to their data by **username** (a logical link, not a foreign key), so deleting a user
keeps their booking and review history.

```mermaid
erDiagram
    USERS {
        bigint id PK
        varchar username UK
        varchar password "BCrypt hash"
        varchar full_name
        varchar email UK
        varchar phone
        varchar role "USER | MANAGER | AGENT | ADMIN"
        bit enabled
        varchar security_question
        varchar security_answer "BCrypt hash"
        bit promo_alerts
        datetime created_at
        datetime last_login_at
    }
    HOTELS {
        bigint id PK
        varchar name
        varchar location
        varchar status "PENDING | APPROVED | REJECTED | REMOVED"
        varchar status_note
        varchar manager_username "-> users.username"
        varchar contact_phone
        varchar contact_email
        varchar description
        varchar amenities "comma separated"
        datetime created_at
    }
    HOTEL_IMAGES {
        bigint hotel_id FK
        varchar image_path
    }
    ROOMS {
        bigint id PK
        bigint hotel_id FK
        varchar room_number
        varchar room_type
        double price
        bit available
        int max_guests
        int bed_count
        varchar bed_type
    }
    ROOM_IMAGES {
        bigint room_id FK
        varchar image_path
    }
    BOOKINGS {
        bigint id PK
        bigint hotel_id FK
        bigint room_id FK
        varchar username "-> users.username"
        varchar reference UK
        varchar group_reference
        date check_in_date
        date check_out_date
        int adults
        int children
        double original_amount
        double discount_amount
        double final_amount
        varchar promo_code
        varchar applied_discount
        double amount_paid
        double balance_due
        varchar status
        varchar refund_id
        bit room_released
    }
    REVIEWS {
        bigint id PK
        bigint hotel_id FK
        bigint booking_id FK "unique"
        varchar username
        int rating "1-5"
        varchar title
        varchar comment
        varchar manager_reply
        bit hidden
    }
    INQUIRIES {
        bigint id PK
        bigint hotel_id FK "null = platform support"
        varchar ticket_number
        varchar created_with_username
        varchar subject
        varchar message
        varchar priority "HIGH | MEDIUM | LOW"
        varchar target_role
        varchar status "PENDING | RESOLVED | IGNORED"
        varchar response
    }
    PROMOTIONS {
        bigint id PK
        bigint hotel_id FK "null = all hotels"
        varchar type "COUPON | DISCOUNT"
        varchar code
        double discount_percentage
        bit active
        datetime valid_from
        datetime valid_until
        varchar terms
    }
    ATTRACTIONS {
        bigint id PK
        bigint hotel_id FK "optional"
        varchar name
        varchar category
        varchar city
        double estimated_cost
        double distance_km
        varchar created_by
    }
    AGENT_PROFILES {
        bigint id PK
        varchar username "-> users.username"
        varchar agency_name
        varchar status "PENDING | NEEDS_INFO | APPROVED"
        int completed_jobs
        int reward_points
        double ranking_score
    }
    ITINERARY_PLANS {
        bigint id PK
        bigint agent_profile_id FK
        varchar title
        int duration_days
        double estimated_cost
    }
    VACATION_REQUESTS {
        bigint id PK
        bigint agent_id FK
        varchar user_username
        varchar status "PENDING_PROPOSAL | PROPOSED | RE_REQUESTED | ACCEPTED | REJECTED"
        date travel_start
        date travel_end
        int travellers
        double budget
        varchar booking_reference
    }
    ITINERARY_REVISIONS {
        bigint id PK
        bigint request_id FK
        int version
        varchar plan
        double price
        varchar change_note
    }
    ITINERARY_REVISION_ATTRACTIONS {
        bigint revision_id FK
        bigint attraction_id FK
    }
    HOTEL_STAFF {
        bigint id PK
        bigint hotel_id FK
        varchar full_name
        varchar role
        varchar email "encrypted"
        varchar phone "encrypted"
        varchar shift
        date start_date
        bit active
    }
    PAYMENTS {
        bigint id PK
        varchar transaction_id UK
        varchar username
        varchar booking_references
        double amount
        varchar card_brand
        varchar card_last4
        varchar card_holder "encrypted"
        varchar status "SUCCEEDED | DECLINED"
    }
    NOTIFICATIONS {
        bigint id PK
        varchar username
        varchar title
        varchar message
        varchar link
        bit read_flag
    }
    ACTIVITY_LOG {
        bigint id PK
        varchar actor
        varchar action
        varchar details
        datetime created_at
    }

    HOTELS ||--o{ HOTEL_IMAGES : has
    HOTELS ||--o{ ROOMS : has
    HOTELS ||--o{ HOTEL_STAFF : employs
    ROOMS ||--o{ ROOM_IMAGES : has
    HOTELS ||--o{ BOOKINGS : receives
    ROOMS ||--o{ BOOKINGS : "booked in"
    HOTELS ||--o{ REVIEWS : "is reviewed in"
    BOOKINGS ||--o| REVIEWS : "reviewed by"
    HOTELS |o--o{ INQUIRIES : "asked about"
    HOTELS |o--o{ PROMOTIONS : "offers"
    HOTELS |o--o{ ATTRACTIONS : "near"
    AGENT_PROFILES ||--o{ ITINERARY_PLANS : publishes
    AGENT_PROFILES ||--o{ VACATION_REQUESTS : receives
    VACATION_REQUESTS ||--o{ ITINERARY_REVISIONS : "has versions"
    ITINERARY_REVISIONS ||--o{ ITINERARY_REVISION_ATTRACTIONS : recommends
    ATTRACTIONS ||--o{ ITINERARY_REVISION_ATTRACTIONS : "recommended in"
```

---

## 2. Class diagram (domain model)

```mermaid
classDiagram
    direction LR
    class AppUser {
        Long id
        String username
        String fullName
        String email
        String role
        boolean enabled
        boolean promoAlerts
        +getRoleLabel() String
    }
    class Hotel {
        Long id
        String name
        String location
        String status
        String managerUsername
        List~String~ imagePaths
        +isApproved() boolean
        +getFromPrice() Double
        +getAmenityList() List~String~
    }
    class Room {
        Long id
        String roomNumber
        String roomType
        Double price
        boolean available
        Integer maxGuests
        +getBedSummary() String
    }
    class Booking {
        Long id
        String reference
        String groupReference
        LocalDate checkInDate
        LocalDate checkOutDate
        double finalAmount
        double amountPaid
        String appliedDiscount
        String status
        +isRoomHeld() boolean
        +getGuestSummary() String
    }
    class Review {
        int rating
        String title
        String comment
        String managerReply
        boolean hidden
    }
    class Inquiry {
        String ticketNumber
        String subject
        String priority
        String status
        String response
    }
    class Promotion {
        PromotionType type
        String code
        Double discountPercentage
        LocalDateTime validUntil
        +getScopeLabel() String
    }
    class Attraction {
        String name
        String category
        String city
        Double estimatedCost
        Double distanceKm
        +isFree() boolean
    }
    class AgentProfile {
        String agencyName
        String status
        int completedJobs
        int rewardPoints
        double rankingScore
    }
    class ItineraryPlan {
        String title
        int durationDays
        double estimatedCost
    }
    class VacationRequest {
        String status
        LocalDate travelStart
        LocalDate travelEnd
        Integer travellers
        +getCurrentRevision() ItineraryRevision
        +isOpenForProposal() boolean
    }
    class ItineraryRevision {
        int version
        String plan
        double price
        String changeNote
    }
    class Notification {
        String username
        String title
        boolean readFlag
    }
    class ActivityLog {
        String actor
        String action
        String details
    }

    Hotel "1" *-- "0..*" Room
    Hotel "1" o-- "0..*" Booking
    Room "1" o-- "0..*" Booking
    Booking "1" -- "0..1" Review
    Hotel "1" o-- "0..*" Review
    Hotel "0..1" -- "0..*" Inquiry
    Hotel "0..1" -- "0..*" Promotion
    Hotel "0..1" -- "0..*" Attraction
    AgentProfile "1" *-- "0..*" ItineraryPlan
    AgentProfile "1" -- "0..*" VacationRequest
    VacationRequest "1" *-- "1..*" ItineraryRevision
    ItineraryRevision "0..*" -- "0..*" Attraction
    AppUser ..> Hotel : manages (managerUsername)
    AppUser ..> Booking : makes (username)
    AppUser ..> Notification : receives
```

---

## 3. Class diagram (design patterns)

```mermaid
classDiagram
    direction TB
    class DiscountStrategy {
        <<interface>>
        +evaluate(Booking) Optional~AppliedDiscount~
    }
    class CouponDiscountStrategy
    class StandardDiscountStrategy
    class PricingService {
        -List~DiscountStrategy~ strategies
        +bestDiscount(Booking) AppliedDiscount
    }
    class BookingService {
        +recalculatePricingForEdit(Booking, Room)
    }
    DiscountStrategy <|.. CouponDiscountStrategy
    DiscountStrategy <|.. StandardDiscountStrategy
    PricingService o-- DiscountStrategy : Strategy
    BookingService --> PricingService

    class BookingEvent {
        <<record>>
        List~Booking~ bookings
        Type type
        String actor
    }
    class HotelEvent {
        <<record>>
    }
    class ReviewEvent {
        <<record>>
    }
    class ApplicationEventPublisher {
        <<Spring>>
        +publishEvent(Object)
    }
    class NotificationListener {
        +onBooking(BookingEvent)
        +onHotel(HotelEvent)
        +onReview(ReviewEvent)
    }
    class AuditLogListener {
        +onBooking(BookingEvent)
        +onHotel(HotelEvent)
        +onReview(ReviewEvent)
    }
    class BookingController
    class HotelService
    class ReviewService
    BookingController --> ApplicationEventPublisher : publishes BookingEvent
    HotelService --> ApplicationEventPublisher : publishes HotelEvent
    ReviewService --> ApplicationEventPublisher : publishes ReviewEvent
    ApplicationEventPublisher ..> NotificationListener : Observer
    ApplicationEventPublisher ..> AuditLogListener : Observer

    class ImageStorageService {
        <<abstract>>
        #folder()* String
        +maxImages()* int
        #itemName()* String
        +validate(files, kept) String
        +store(files) List~String~
        +delete(path)
    }
    class HotelImageStorageService
    class RoomImageStorageService
    ImageStorageService <|-- HotelImageStorageService : Template Method
    ImageStorageService <|-- RoomImageStorageService : Template Method
```

---

## 4. Booking status (state diagram)

```mermaid
stateDiagram-v2
    [*] --> PENDING : guest submits (BookingEvent CREATED)
    PENDING --> APPROVED : manager approves (no date overlap)
    PENDING --> REFUND_PENDING : manager rejects
    PENDING --> CANCELLED : guest / manager cancels (nothing paid)
    APPROVED --> PENDING : guest edits to a dearer stay (top-up needed)
    APPROVED --> REFUND_PENDING : rejected / cancelled after payment / cheaper edit
    REFUND_PENDING --> REFUND_COMPLETED : manager pays refund
    REFUND_COMPLETED --> REFUND_ACCEPTED : guest confirms
    CANCELLED --> [*]
    REFUND_ACCEPTED --> [*]
    APPROVED --> [*] : stay completed (guest can review)
```

---

## 5. Use case diagrams

Mermaid has no UML use-case notation, so actors are drawn as rounded nodes, use cases as ovals and the
system boundary as a box. 5.1 shows which actors take part in each major function. 5.2 - 5.5 show each
actor's use cases, and together they cover every use case in the system.

### 5.1 Overview - actors and the six major functions

```mermaid
flowchart LR
    Visitor(["Visitor"])
    Tourist(["Tourist"])
    Manager(["Hotel Manager"])
    Agent(["Travel Agent"])
    Admin(["Administrator"])
    subgraph SYS["Web-based Hotel Reservation System"]
        F1(["1. Hotel Management"])
        F2(["2. Room Management"])
        F3(["3. Booking Management"])
        F4(["4. Itinerary and Local Experience"])
        F5(["5. Promotion Management"])
        F6(["6. Inquiry and Feedback"])
        MIN(["Minor: login, password reset, profile, notifications"])
    end
    Gateway[["Payment gateway"]]
    Mail[["Email service"]]
    Visitor --- F1 & F3 & MIN
    Tourist --- F3 & F4 & F5 & F6 & MIN
    F1 & F2 & F3 & F4 & F5 & F6 & MIN --- Manager
    F4 & MIN --- Agent
    F1 & F4 & F5 & F6 & MIN --- Admin
    F3 -.-> Gateway
    F3 & F5 -.-> Mail
```

### 5.2 Tourist and Visitor

```mermaid
flowchart LR
    Visitor(["Visitor"])
    Tourist(["Tourist"])
    subgraph SYS["Web-based Hotel Reservation System"]
        U1(["Search hotels by location, price, amenities, dates, guests"])
        U2(["View hotel, rooms, reviews, agent profiles"])
        U3(["Register, log in, reset password"])
        U4(["Book one or more rooms"])
        U5(["Pay online by card"])
        U6(["Apply coupon or discount"])
        U7(["Edit or cancel booking"])
        U8(["View activities near a stay"])
        U9(["Request trip from an agent and accept or ask for changes"])
        U10(["Raise prioritised inquiry ticket"])
        U11(["Review a completed stay"])
        U12(["Edit profile, travel preferences, promo alerts"])
        U13(["Notifications"])
    end
    Gateway[["Payment gateway"]]
    Visitor --- U1 & U2 & U3
    Tourist --- U1 & U2 & U4 & U7 & U8 & U9 & U10 & U11 & U12 & U13
    U4 -. "include" .-> U1
    U5 -. "extend" .-> U4
    U6 -. "extend" .-> U4
    U5 -.-> Gateway
```

### 5.3 Hotel Manager

```mermaid
flowchart LR
    Manager(["Hotel Manager"])
    subgraph SYS["Web-based Hotel Reservation System"]
        M1(["Register hotel for approval"])
        M2(["Manage hotel details, amenities, photos"])
        M3(["Manage hotel staff"])
        M4(["Add, edit, remove rooms"])
        M5(["Set room price and availability"])
        M6(["Approve or reject bookings"])
        M7(["Cancel booking, release room, settle refund"])
        M8(["Export bookings report"])
        M9(["Create coupons and discounts"])
        M10(["Email coupon to tourists"])
        M11(["Respond to inquiries"])
        M12(["Reply to reviews"])
        M13(["Add nearby attractions"])
    end
    Mail[["Email service"]]
    Manager --- M1 & M2 & M3 & M4 & M5 & M6 & M7 & M8 & M9 & M10 & M11 & M12 & M13
    M6 -.-> Mail
    M10 -.-> Mail
```

### 5.4 Travel Agent

```mermaid
flowchart LR
    Agent(["Travel Agent"])
    subgraph SYS["Web-based Hotel Reservation System"]
        A1(["Maintain public profile and sample itineraries"])
        A2(["Recommend local attractions"])
        A3(["Receive trip requests"])
        A4(["Send versioned itinerary proposal"])
        A5(["Track performance and reward points"])
    end
    Agent --- A1 & A2 & A3 & A4 & A5
    A4 -. "include" .-> A2
```

### 5.5 Administrator

```mermaid
flowchart LR
    Admin(["Administrator"])
    subgraph SYS["Web-based Hotel Reservation System"]
        D1(["Approve or reject hotels"])
        D2(["Remove hotels safely"])
        D3(["Verify travel agents"])
        D4(["Suspend, activate, delete users"])
        D5(["Moderate reviews"])
        D6(["Platform-wide promotions"])
        D7(["Dashboard KPIs and charts"])
        D8(["Monitor system health and performance"])
        D9(["Activity log and CSV reports"])
    end
    Admin --- D1 & D2 & D3 & D4 & D5 & D6 & D7 & D8 & D9
```

---

## 6. Activity diagrams (one per major function)

Each diagram follows the real code path, including the checks that can stop the flow.

### 6.1 Hotel Management - `HotelController`, `HotelService`, `StaffController`

```mermaid
flowchart TD
    A([Manager opens Register hotel]) --> B[Enter name, location, contact, amenities, photos]
    B --> C{"Valid? name and location set, 1-8 photos, JPG/PNG/WEBP up to 5MB"}
    C -- No --> B
    C -- Yes --> D[Save hotel as PENDING, owned by the manager]
    D --> E[Activity log entry]
    E --> F{Admin decision}
    F -- Approve --> G[APPROVED - visible to tourists, manager notified]
    F -- Reject with reason --> H[REJECTED - manager notified]
    H --> I[Manager edits the hotel] --> J[Re-submitted as PENDING] --> F
    G --> K[Manager keeps details, amenities, photos and staff up to date]
    K --> L{Remove hotel?}
    L -- No --> K
    L -- Yes --> M[Open bookings cancelled, paid ones go to REFUND_PENDING]
    M --> N[Guests notified, hotel hidden, booking history kept]
    N --> Z([End])
```

### 6.2 Room Management - `RoomController`, `RoomService`

```mermaid
flowchart TD
    A([Manager opens one of their hotels]) --> B{Owner of this hotel?}
    B -- No --> X[Refused: only your own hotels] --> Z([End])
    B -- Yes --> C{Action}
    C -- Add or edit --> D[Room number, type, price, guests, beds, photos, available]
    D --> E{"Valid? price above 0 and up to 100000, guests 1-20, beds 1-10, number unique in hotel"}
    E -- No --> D
    E -- Yes --> F[Saved - new price applies to future bookings]
    C -- Delete --> G{Room has booking history?}
    G -- Yes --> H[Marked unavailable instead of deleted]
    G -- No --> I[Room and its photos deleted]
    C -- Guest left early --> J[Release room - free again for new guests]
    F --> Z
    H --> Z
    I --> Z
    J --> Z
```

### 6.3 Booking Management - `BookingController`, `PaymentService`, `BookingService`

```mermaid
flowchart TD
    A([Tourist searches by location, price, amenities, dates, guests]) --> B[Choose a hotel and one or more rooms]
    B --> C{"Dates valid, at least 1 adult per room, coupon valid, rooms free those nights?"}
    C -- No --> B
    C -- Yes --> D[Create bookings as PENDING, priced after the best discount]
    D --> E{Payment method}
    E -- Card --> F[Checkout: card holder, number, expiry, CVV]
    F --> G{Card passes Luhn, expiry and CVV checks?}
    G -- No --> F
    G -- Yes --> H{Lock the room - still no approved overlap?}
    H -- Taken meanwhile --> I[Not charged - choose another room or dates] --> B
    H -- Free --> J{Gateway charges the card}
    J -- Declined --> F
    J -- Approved --> K[Booking APPROVED instantly, receipt shown]
    E -- Bank transfer --> L[Upload receipt - booking stays PENDING]
    L --> M{Manager decision}
    M -- Approve --> N{Lock the room - still no approved overlap?}
    N -- Clash --> O[Approval refused] --> M
    N -- Free --> K
    M -- Reject --> P[REFUND_PENDING, refund paid, guest confirms]
    K --> Q[Confirmation email and in-app notice to tourist and manager]
    Q --> Z([End])
    P --> Z
```

### 6.4 Itinerary and Local Experience - `AgentController`, `AgentService`, `AttractionController`

```mermaid
flowchart TD
    A([Agent saves profile]) --> B[Status PENDING]
    B --> C{Admin verifies}
    C -- Needs more info --> D[Admin note sent to agent] --> A
    C -- Approve --> E[Public profile, sample itineraries, recommended attractions]
    E --> F[Tourist sends trip request: dates, travellers, budget, preferences]
    F --> G{"Valid? email, start not in the past, 1-50 travellers, budget not negative"}
    G -- No --> F
    G -- Yes --> H[Agent sends itinerary version N with price and attractions]
    H --> I{Tourist decision}
    I -- Request changes with feedback --> J[RE_REQUESTED] --> H
    I -- Reject --> K[REJECTED]
    I -- Accept --> L[ACCEPTED - agent gets +1 job and +10 reward points]
    L --> Z([End])
    K --> Z
    T([Tourist opens My bookings]) --> U[Nearby attractions shown for each upcoming stay] --> Z
```

### 6.5 Promotion Management - `PromotionController`, `PromotionService`, pricing strategies

```mermaid
flowchart TD
    A([Manager or admin creates an offer]) --> B{Coupon or standard discount?}
    B --> C["Title, percentage, terms, validity, hotel, plus a code for coupons"]
    C --> D{"Own approved hotel, 1-90 percent, code 3-30 characters and unique, end after start?"}
    D -- No --> C
    D -- Yes --> E[Save promotion]
    E --> F{Active?}
    F -- Yes --> G[In-app alert to opted-in tourists whose preferred destination matches the hotel]
    F -- No --> H
    G --> H{Email the coupon now?}
    H -- Yes --> I[Email code, value, terms and expiry to those tourists]
    H -- No --> J
    I --> J[Tourist books with a coupon and/or picks a discount]
    J --> K[Strategy pattern: the best single discount applies, never combined]
    K --> Z([End])
```

### 6.6 Inquiry and Feedback - `InquiryController`, `ReviewController`

```mermaid
flowchart TD
    A([Tourist raises an inquiry ticket]) --> B[Hotel, subject, message, priority LOW / MEDIUM / HIGH]
    B --> C{"Subject 3-150 characters, message 10-1000, valid email?"}
    C -- No --> B
    C -- Yes --> D[Ticket number issued, hotel manager notified]
    D --> E{While PENDING the tourist may}
    E -- Edit or change priority --> D
    E -- Withdraw --> Z([End])
    E -- Wait --> F{Manager action}
    F -- Respond --> G[RESOLVED - tourist notified]
    F -- Close with reason --> H[IGNORED - tourist notified]
    G --> Z
    H --> Z
    R([Stay completed]) --> S{"Approved booking, own stay, not reviewed yet?"}
    S -- No --> Z
    S -- Yes --> T[Rating 1-5, title, comment - published on the hotel page]
    T --> U[Manager replies, admin can hide or restore] --> Z
```
