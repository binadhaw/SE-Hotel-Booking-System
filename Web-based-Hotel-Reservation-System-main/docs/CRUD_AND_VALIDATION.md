# CRUD Operations & Input Validation

Every module supports create, read, update and delete through the UI, backed by MySQL.
Validation happens on the **server** (Jakarta Bean Validation annotations on the entities/forms plus business
rules in the services/controllers); HTML5 attributes (`required`, `min`, `max`, `maxlength`, `type=email`)
give instant feedback in the browser but are never relied on.

## CRUD matrix

| Module (owner) | Create | Read | Update | Delete |
|---|---|---|---|---|
| **User accounts** | Sign up `/register` | Admin list & search `/admin/users`, own `/profile` | Profile & password `/profile`; admin suspend/activate | Admin delete |
| **Hotels** (manager / admin) | `/hotels/new` | Search `/hotels`, detail `/hotels/{id}` | `/hotels/{id}/edit` (photos, amenities, contact); admin approve/reject | Remove (soft - keeps booking history) |
| **Rooms** (owning manager) | `/hotels/{id}/rooms/new` | `/hotels/{id}/rooms`, hotel page | `/rooms/{id}/edit` | Delete (booked rooms are made unavailable instead) |
| **Bookings** (tourist / manager) | `/bookings/new/{hotelId}` (one or many rooms) | `/bookings` | Guest edits dates/room/guests; manager approve, reject, release room, refund | Guest cancel; manager cancel; manager deletes closed records |
| **Reviews** (tourist) | After a completed stay | Hotel page, rating on search | Edit own review; manager reply; admin hide/restore | Delete own review |
| **Inquiries** (tourist, agent, manager) | `/inquiries/new` | `/inquiries` (sent & received) | Sender edits while pending; recipient responds/closes | Sender withdraws while pending; recipient deletes |
| **Promotions** (manager / admin) | Coupon & discount forms | `/promotions` | `/promotions/{id}/edit`; email coupon | Delete |
| **Attractions** (agent / manager) | Agent portal, hotel page | Hotel page, agent profile | `/attractions/{id}/edit` | Delete |
| **Agent profile** (agent) | Agent portal | Public profile `/agents/{id}/profile` | Agent portal; admin approve / request info | Admin removes agent |
| **Sample itineraries** (agent) | Agent portal | Public profile | Inline edit in portal | Remove |
| **Trip requests & itineraries** (tourist / agent) | `/agents/explore` | Tourist & agent views with version history | Agent sends new versions; tourist accepts / asks for changes / declines | Tourist withdraws (until accepted) |
| **Hotel staff** (owning manager; admin views) | `/hotels/{id}/staff` | Staff list per hotel | `/staff/{id}/edit` | Remove |
| **Online payments** (tourist) | `/payments/checkout` | Receipt `/payments/{txn}`, card shown on bookings | Balance top-up after a dearer edit | (financial record - never deleted) |
| **Travel preferences** (tourist) | Profile | Profile, home recommendations | Profile | Clear the fields |
| **Notifications** (everyone) | Created by events | Bell & `/notifications` | Mark read / mark all read | Delete one / clear read |

## Validation rules (server side)

| Form | Rules |
|---|---|
| Registration | Username 3-30 chars, letters/numbers/`._-`, unique; valid unique email; phone format; password 8-64 chars with letters and digits, confirmed; security question from list; role cannot be ADMIN; privacy notice must be accepted (promotional emails opt-in) |
| Profile / password | Valid unique email; phone format; current password checked; new password rules as above |
| Hotel | Name 2-100; location required (max 100); description max 255; phone & email format; amenities only from the allowed list; 1-8 photos, JPG/PNG/WEBP, max 5 MB each |
| Room | Room number required & unique within the hotel; type required; price > 0; guests 1-20; beds 1-10; bed type from list; 1-6 photos |
| Booking | At least one room of this hotel, max 10; check-in not in the past; check-out after check-in; no overlap with an approved booking; 1+ adult per room; coupon must be valid for the hotel; receipt JPG/PNG/WEBP/PDF only |
| Review | Only own, completed, approved stay; one per booking; rating 1-5; title max 120; comment max 2000 |
| Inquiry | Name max 100; valid email; subject 3-150; message 10-1000; priority from list; hotel must be approved |
| Promotion | Title required (max 255); discount 1-90%; coupon code 3-30 `[A-Z0-9_-]` and unique; end after start; manager may only target own approved hotels; terms max 500 |
| Attraction | Name & town required; category from list; cost >= 0; distance >= 0; manager may only pin to own hotel |
| Agent profile | Name required (max 100); phone format; experience 0-60; documents link must be http(s); text fields length-limited; status/score never taken from the form |
| Trip request | Name max 100; valid email; preferences 1-1000 chars; start not in the past; end after start; travellers 1-50; budget >= 0; linked booking must belong to the tourist; agent must be approved |
| Sample itinerary | Title required; 1-60 days; cost >= 0 |
| Card payment | Name 2-60 letters; card number passes the Luhn check; Visa / Mastercard / Amex only; expiry MM/YY not in the past; CVV 3 digits (4 for Amex); room must still be free; only the guest's own unpaid bookings; full number and CVV never stored |
| Hotel staff | Name required (max 100); role and shift from lists; valid email and phone; start date required and plausible; notes max 255; only the hotel's own manager |
| Travel preferences | Interests only from the attraction categories; destination max 80; budget $1-$100,000 |

Automated tests covering these rules: `mvnw test` (JUnit) - see `src/test/java`.
