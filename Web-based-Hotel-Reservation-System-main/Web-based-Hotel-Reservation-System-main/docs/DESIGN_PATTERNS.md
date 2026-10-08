# Design Patterns Used in the Hotel Reservation System

The specification awards full marks for *"at least 2 relevant patterns implemented correctly, well-integrated
across modules, and clearly justified with code references (where/why used and benefits)"*.
This system uses three Gang-of-Four patterns in its own code, plus patterns supplied by the Spring framework.
Each pattern below lists **where** it is, **why** it was needed, the **benefit**, and **how to demonstrate it**.

All paths are relative to `src/main/java/com/Reservation/Hotel/`.

---

## 1. Strategy (behavioural) - discount pricing

**Where**

| Role in the pattern | Class |
|---|---|
| Strategy interface | `pricing/DiscountStrategy.java` |
| Concrete strategy - typed-in coupon code | `pricing/CouponDiscountStrategy.java` |
| Concrete strategy - standard (seasonal) discount | `pricing/StandardDiscountStrategy.java` |
| Context (chooses the best strategy) | `pricing/PricingService.java` |
| Result value object | `pricing/AppliedDiscount.java` |
| Client | `service/BookingService.recalculatePricingForEdit()` |

```java
public interface DiscountStrategy {
    Optional<AppliedDiscount> evaluate(Booking booking);
}

@Service
public class PricingService {
    private final List<DiscountStrategy> strategies;          // Spring injects every strategy bean
    public AppliedDiscount bestDiscount(Booking booking) {
        return strategies.stream().map(s -> s.evaluate(booking)).flatMap(Optional::stream)
                .max(Comparator.comparingDouble(AppliedDiscount::rate)).orElse(AppliedDiscount.NONE);
    }
}
```

**Why** - A guest can get a discount in more than one way (coupon code, standard discount, and in future
perhaps long-stay or loyalty discounts). Originally all the rules were in one `if/else` method inside
`BookingService` that also hard-coded the coupon codes `SAVE10` and `WELCOME20`.

**Benefits**
- *Open/Closed principle*: a new discount type is one new `@Component` class; `BookingService`,
  `PricingService` and the controllers do not change (`DesignPatternsTest.strategyANewDiscountTypeNeedsNoChangesToExistingCode`).
- Each rule is small and unit-testable on its own.
- The "best offer wins, never stacked" business rule lives in exactly one place.
- The chosen strategy's label is stored on the booking (`Booking.appliedDiscount`) and shown to the guest,
  e.g. *"Coupon SAVE10 (10%)"*.

**Demonstrate** - book a room with coupon `SAVE10` and also pick a 20% standard discount: the bookings
list shows only the 20% discount was applied.

---

## 2. Observer (behavioural) - domain events

**Where**

| Role in the pattern | Class |
|---|---|
| Events (subject's messages) | `events/BookingEvent.java`, `events/HotelEvent.java`, `events/ReviewEvent.java` |
| Publishers (subjects) | `controller/BookingController`, `service/HotelService`, `controller/HotelController`, `service/ReviewService` - via Spring's `ApplicationEventPublisher` |
| Observer 1 - notifications & emails | `events/listeners/NotificationListener.java` |
| Observer 2 - audit trail | `events/listeners/AuditLogListener.java` |

```java
// Publisher - BookingController.approveBooking(): announces what happened, nothing more
events.publishEvent(new BookingEvent(booking, BookingEvent.Type.APPROVED, authentication.getName(), null));

// Observer - reacts independently
@EventListener
public void onBooking(BookingEvent e) { ... send receipt email, notify guest ... }   // NotificationListener
@EventListener
public void onBooking(BookingEvent e) { activityLog.log(e.actor(), "BOOKING_" + e.type(), ...); }   // AuditLogListener
```

**Why** - Approving, rejecting or cancelling a booking, approving/removing a hotel and posting a review each
trigger several side effects (email the guest, alert the manager, write the admin activity log). Before the
refactor every controller method called the email service, notification service and activity log itself, so
the same code was repeated across modules and was easy to forget.

**Benefits**
- *Loose coupling*: booking/hotel/review code does not know who is listening.
- New reactions (SMS, analytics, push notifications) are new listener classes - no change to business code.
- Each listener has a single responsibility; the audit log is guaranteed for every event type.
- One event fans out to many observers (`DesignPatternsTest.observerOneEventReachesEveryListener`).

**Demonstrate** - approve a booking as the manager, then show (1) the guest's notification bell,
(2) the email in the log/inbox and (3) the admin *Activity log* - all from one `publishEvent` call.

---

## 3. Template Method (behavioural) - photo storage

**Where**

| Role in the pattern | Class |
|---|---|
| Abstract class with the template methods `validate()`, `store()`, `delete()` (marked `final`) | `service/ImageStorageService.java` |
| Concrete class - hotel photos (folder `hotels`, max 8) | `service/HotelImageStorageService.java` |
| Concrete class - room photos (folder `rooms`, max 6) | `service/RoomImageStorageService.java` |
| Clients | `controller/HotelController`, `controller/RoomController`, `service/HotelService`, `service/RoomService` |

```java
public abstract class ImageStorageService {
    protected abstract String folder();      // hook
    public abstract int maxImages();         // hook
    protected abstract String itemName();    // hook

    public final String validate(List<MultipartFile> files, int alreadyKept) { ... uses maxImages(), itemName() ... }
    public final List<String> store(List<MultipartFile> files) { ... uses folder() ... }
}
```

**Why** - Hotel and room photo handling were two ~100-line classes that were identical except for the
folder name, the photo limit and the word "hotel"/"room" in messages. Duplicated security-sensitive code
(file type and size checks) is a maintenance risk - a fix in one copy can be missed in the other.

**Benefits**
- The algorithm (count check -> size check -> type whitelist -> random file name -> save) exists once.
- `final` template methods guarantee every subclass gets the same security checks.
- A new photo type (e.g. attraction photos) is a 20-line subclass.

---

## 4. Payment gateway abstraction (Strategy / ports-and-adapters)

`payment/PaymentGateway.java` is the only thing the booking code talks to when charging a card;
`payment/SimulatedPaymentGateway.java` is the implementation used in the project. A real provider (PayHere,
Stripe...) would be one more implementation of the same interface (an *Adapter* around the provider's SDK),
chosen by configuration - `PaymentService` and the controllers would not change. The successful payment is
announced with a `BookingEvent.PAID_ONLINE`, so the Observer listeners send the receipt, notify the guest and
the hotel manager and write the audit log without any extra code in the payment flow.

---

## 5. Patterns provided by the framework (mention in the viva)

| Pattern | Where | Note |
|---|---|---|
| **Singleton** | Every `@Service`, `@Controller`, `@Component` | Spring creates one shared instance per bean. |
| **Dependency Injection / Inversion of Control** | Constructor injection everywhere, e.g. `BookingService(BookingRepository, PricingService)` | Makes the Strategy list and the event publisher pluggable and the classes unit-testable with mocks. |
| **Repository** | `repository/*Repository.java` (Spring Data JPA) | Hides SQL behind collection-like interfaces. |
| **MVC** | `controller/` + `templates/` + `model/` | Separates request handling, views and data. |

---

## Tests that prove the patterns work

`src/test/java/com/Reservation/Hotel/DesignPatternsTest.java` (run with `mvnw test`):

| Test | Shows |
|---|---|
| `strategyEveryDiscountSourceIsAPluggableBean` | Both concrete strategies are registered automatically |
| `strategyPricingPicksTheSingleBestDiscount` | Best offer wins; offers are not stacked |
| `strategyANewDiscountTypeNeedsNoChangesToExistingCode` | Open/Closed - a new strategy plugs straight in |
| `observerOneEventReachesEveryListener` | One `BookingEvent` writes the audit log *and* notifies guest + manager |
| `templateMethodSharedAlgorithmDifferentHooks` | Same validation for hotels and rooms, different limits/messages |
