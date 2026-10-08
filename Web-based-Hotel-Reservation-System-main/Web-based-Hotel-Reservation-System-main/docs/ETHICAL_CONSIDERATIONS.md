# Ethical Considerations

Design-document section required by the SE2030 specification ("Describe any ethical concerns and how they
are addressed - e.g. data privacy, user consent, accessibility"). Every measure below is implemented in the code.

| Concern | Risk in a hotel booking platform | How the system addresses it | Where |
|---|---|---|---|
| **Data privacy** | Tourists hand over names, phone numbers, card details and travel plans | Phone numbers, card holder names and contact details encrypted at rest (AES-256-GCM); passwords and security answers stored as BCrypt hashes; card number and CVV never stored (brand + last 4 only); encryption key kept out of Git (`secrets.properties`); optional HTTPS profile | `security/FieldEncryptor`, `security/EncryptedStringConverter`, `payment/PaymentService`, `config/PasswordConfig` |
| **Least privilege** | Hotel staff or agents seeing other businesses' guests | Role-based access (Tourist, Hotel Manager, Travel Agent, Admin); managers only see bookings, inquiries and offers for their own hotels; agents only see requests sent to them; admins cannot be self-registered | `config/SecurityConfig`, ownership checks in `HotelService.canManage` and controllers |
| **User consent** | Using personal data or sending marketing without permission | Sign-up requires accepting a privacy notice (`/privacy`); promotional emails are **opt-in** (unticked at sign-up) and can be switched off on the profile; travel preferences are optional and only used for recommendations | `dto/RegistrationForm` (`@AssertTrue acceptPrivacy`), `UserService.register`, `templates/privacy.html`, `ProfileController` |
| **Transparency** | Hidden fees or misleading prices | Price breakdown with the discount shown before paying; a coupon and a standard discount are never stacked silently (the better one applies and is named); the payment gateway is labelled as a demo | `pricing/PricingService`, `templates/payments/checkout.html` |
| **Fairness & honest reviews** | Fake or manipulated reviews | Only guests with a completed, approved stay can review, one review per booking; managers can reply publicly but cannot edit or delete guest reviews; admin moderation hides abusive reviews without deleting them (auditable) | `service/ReviewService`, `ReviewController` |
| **Accountability** | Unchecked admin power over hotels and users | Every approval, rejection, removal, suspension and deletion is written to the activity log; removing a hotel cancels or refunds open bookings and notifies guests instead of silently deleting them | `events/listeners/AuditLogListener`, `HotelService.removeHotel` |
| **Reliability towards customers** | Taking payment for a room that is already sold | Room row lock before every confirmation so a room is never sold twice; declined cards never confirm a booking | `RoomRepository.lockAllById`, `ConcurrentBookingTest` |
| **Accessibility** | Elderly or less tech-savvy users (a limitation noted in the proposal) | Consistent Bootstrap 5 layout, labelled form fields, inline validation messages in plain language, ARIA labels on icon-only controls, friendly error pages without technical details | `templates/`, `controller/GlobalExceptionHandler` |
| **Security of uploads** | Malicious files uploaded as receipts or photos | Only images/PDF accepted, random file names, size limits; payment receipts are served only to the booking's guest, that hotel's manager and admins (direct `/uploads/receipts/` URLs are blocked) | `service/ImageStorageService`, `BookingController.receiptProblem`, `BookingController.receiptFile` |

## Known limitations (honest disclosure)

- Users cannot delete their own account from the UI; an administrator removes it on request (booking history is
  kept for the hotel's records).
- Single language (English) and currency (USD), as stated in the proposal's scope.
