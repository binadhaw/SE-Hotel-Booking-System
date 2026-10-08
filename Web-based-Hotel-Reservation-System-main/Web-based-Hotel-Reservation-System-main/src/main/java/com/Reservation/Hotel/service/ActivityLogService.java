package com.Reservation.Hotel.service;

import com.Reservation.Hotel.model.ActivityLog;
import com.Reservation.Hotel.repository.ActivityLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);

    private final ActivityLogRepository repository;

    public ActivityLogService(ActivityLogRepository repository) {
        this.repository = repository;
    }

    /** Records an action. Never throws - auditing must not break the action itself. */
    public void log(String actor, String action, String details) {
        try {
            String d = details == null ? null : (details.length() > 500 ? details.substring(0, 500) : details);
            repository.save(new ActivityLog(actor, action, d));
        } catch (Exception e) {
            log.warn("Could not record activity {}: {}", action, e.getMessage());
        }
    }

    public List<ActivityLog> recent() {
        return repository.findTop15ByOrderByCreatedAtDesc();
    }

    /** @param prefix "" for everything, or HOTEL / USER / BOOKING / AGENT / REVIEW / PROMOTION */
    public Page<ActivityLog> page(String prefix, int page) {
        return repository.findByActionStartingWithOrderByCreatedAtDesc(prefix == null ? "" : prefix,
                PageRequest.of(Math.max(page, 0), 50));
    }
}
