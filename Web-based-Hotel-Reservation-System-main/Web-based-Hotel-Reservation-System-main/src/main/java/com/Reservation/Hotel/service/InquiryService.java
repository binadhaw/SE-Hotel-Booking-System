package com.Reservation.Hotel.service;

import com.Reservation.Hotel.model.AppUser;
import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.model.Inquiry;
import com.Reservation.Hotel.repository.InquiryRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Inquiries / support tickets. A tourist's (or agent's) inquiry about a hotel goes to that hotel's manager;
 * general questions and managers' own questions go to the administrators.
 */
@Service
public class InquiryService {

    private final InquiryRepository inquiryRepository;
    private final NotificationService notificationService;
    private final UserService userService;

    public InquiryService(InquiryRepository inquiryRepository, NotificationService notificationService,
                          UserService userService) {
        this.inquiryRepository = inquiryRepository;
        this.notificationService = notificationService;
        this.userService = userService;
    }

    public List<Inquiry> getReceivedInquiries(Authentication authentication, String priority) {
        List<Inquiry> list;
        if (HotelService.hasRole(authentication, "ADMIN")) {
            list = inquiryRepository.findByTargetRole("ADMIN");
        } else if (HotelService.hasRole(authentication, "MANAGER")) {
            list = inquiryRepository.findForManager(authentication.getName());
        } else {
            list = List.of();
        }
        return sortAndFilter(list, priority);
    }

    public List<Inquiry> getSubmittedInquiries(Authentication authentication, String priority) {
        return sortAndFilter(inquiryRepository.findByCreatedWithUsername(authentication.getName()), priority);
    }

    /** Urgent (HIGH priority) open tickets first, then newest first. */
    private List<Inquiry> sortAndFilter(List<Inquiry> list, String priority) {
        return list.stream()
                .filter(i -> priority == null || priority.isBlank() || priority.equalsIgnoreCase("ALL")
                        || priority.equalsIgnoreCase(i.getPriority()))
                .sorted(Comparator.comparing((Inquiry i) -> !"PENDING".equals(i.getStatus()))
                        .thenComparing(i -> !"HIGH".equals(i.getPriority()))
                        .thenComparing(Inquiry::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /** May this user answer / delete the inquiry? */
    public boolean canHandle(Inquiry inquiry, Authentication authentication) {
        if (inquiry == null) return false;
        if ("ADMIN".equals(inquiry.getTargetRole())) return HotelService.hasRole(authentication, "ADMIN");
        if (!HotelService.hasRole(authentication, "MANAGER")) return false;
        return inquiry.getHotel() == null
                || authentication.getName().equalsIgnoreCase(inquiry.getHotel().getManagerUsername());
    }

    public Inquiry getInquiryById(Long id) {
        return inquiryRepository.findById(id).orElse(null);
    }

    /** Fills in sender/recipient, gives it a ticket number and alerts whoever has to answer it. */
    @Transactional
    public Inquiry submit(Inquiry inquiry, Hotel hotel, Authentication authentication) {
        inquiry.setId(null);
        inquiry.setCreatedWithUsername(authentication.getName());
        inquiry.setStatus("PENDING");
        inquiry.setResponse(null);
        inquiry.setCreatedAt(LocalDateTime.now());
        if (!List.of("HIGH", "MEDIUM", "LOW").contains(inquiry.getPriority())) inquiry.setPriority("MEDIUM");
        inquiry.setTicketNumber("TCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase());

        boolean manager = HotelService.hasRole(authentication, "MANAGER");
        inquiry.setSenderRole(manager ? "MANAGER" : HotelService.hasRole(authentication, "AGENT") ? "AGENT" : "USER");
        if (!manager && hotel != null) {
            inquiry.setHotel(hotel);
            inquiry.setTargetRole("MANAGER");
        } else {
            inquiry.setHotel(null);
            inquiry.setTargetRole("ADMIN");
        }
        Inquiry saved = inquiryRepository.save(inquiry);

        String title = ("HIGH".equals(saved.getPriority()) ? "URGENT inquiry " : "New inquiry ") + saved.getTicketNumber();
        String message = saved.getSubject() + " - from " + saved.getName();
        if (saved.getHotel() != null) {
            notificationService.notifyInApp(saved.getHotel().getManagerUsername(), title + " - " + saved.getHotel().getName(), message, "/inquiries");
        } else {
            for (AppUser admin : userService.findByRole(AppUser.ROLE_ADMIN)) {
                notificationService.notifyInApp(admin.getUsername(), title, message, "/inquiries");
            }
        }
        return saved;
    }

    /** Records the answer (or the reason for closing it) and notifies the person who asked. */
    @Transactional
    public void respond(Inquiry inquiry, String response, boolean ignore, String responder) {
        inquiry.setResponse(response == null ? null : response.trim());
        inquiry.setStatus(ignore ? "IGNORED" : "RESOLVED");
        inquiry.setRespondedBy(responder);
        inquiry.setRespondedAt(LocalDateTime.now());
        inquiryRepository.save(inquiry);
        notificationService.notify(inquiry.getCreatedWithUsername(),
                (ignore ? "Inquiry closed: " : "Reply to your inquiry: ") + inquiry.getDisplayTicket(),
                "\"" + inquiry.getSubject() + "\" - " + inquiry.getResponse(), "/inquiries");
    }

    /** The sender may change or withdraw an inquiry until it has been answered. */
    public boolean canEditOwn(Inquiry inquiry, Authentication authentication) {
        return inquiry != null && "PENDING".equals(inquiry.getStatus())
                && authentication.getName().equals(inquiry.getCreatedWithUsername());
    }

    @Transactional
    public void updateOwn(Inquiry existing, Inquiry form, Hotel hotel) {
        existing.setName(form.getName().trim());
        existing.setEmail(form.getEmail().trim());
        existing.setSubject(form.getSubject().trim());
        existing.setMessage(form.getMessage().trim());
        existing.setPriority(List.of("HIGH", "MEDIUM", "LOW").contains(form.getPriority()) ? form.getPriority() : "MEDIUM");
        if (!"MANAGER".equals(existing.getSenderRole())) {
            existing.setHotel(hotel);
            existing.setTargetRole(hotel != null ? "MANAGER" : "ADMIN");
        }
        inquiryRepository.save(existing);
    }

    public Inquiry saveInquiry(Inquiry inquiry) {
        return inquiryRepository.save(inquiry);
    }

    public void deleteInquiry(Long id) {
        inquiryRepository.deleteById(id);
    }

    public long countPending() {
        return inquiryRepository.countByStatus("PENDING");
    }
}
