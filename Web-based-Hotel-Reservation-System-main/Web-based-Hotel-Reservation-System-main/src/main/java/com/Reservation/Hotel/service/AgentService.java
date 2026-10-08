package com.Reservation.Hotel.service;

import com.Reservation.Hotel.model.AgentProfile;
import com.Reservation.Hotel.model.Attraction;
import com.Reservation.Hotel.model.ItineraryPlan;
import com.Reservation.Hotel.model.ItineraryRevision;
import com.Reservation.Hotel.model.VacationRequest;
import com.Reservation.Hotel.repository.AgentProfileRepository;
import com.Reservation.Hotel.repository.VacationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private final AgentProfileRepository agentRepository;
    private final VacationRequestRepository vacationRequestRepository;
    private final NotificationService notificationService;

    public AgentService(AgentProfileRepository agentRepository, VacationRequestRepository vacationRequestRepository,
                        NotificationService notificationService) {
        this.agentRepository = agentRepository;
        this.vacationRequestRepository = vacationRequestRepository;
        this.notificationService = notificationService;
    }

    // ---------------- profiles ----------------

    public AgentProfile getProfileByUsername(String username) {
        return agentRepository.findByUsername(username).orElse(null);
    }

    public AgentProfile getProfileById(Long id) {
        return id == null ? null : agentRepository.findById(id).orElse(null);
    }

    /**
     * Saves only the fields an agent is allowed to edit. Status, admin note, jobs, points and score are
     * never taken from the form. Editing the profile after an admin asked for more information puts it
     * back in the review queue.
     */
    @Transactional
    public AgentProfile saveOwnProfile(String username, AgentProfile form) {
        AgentProfile profile = getProfileByUsername(username);
        boolean isNew = profile == null;
        if (isNew) {
            profile = new AgentProfile();
            profile.setUsername(username);
            profile.setStatus("PENDING");
        }
        profile.setAgencyName(form.getAgencyName());
        profile.setPhone(form.getPhone());
        profile.setExperienceYears(Math.max(0, Math.min(60, form.getExperienceYears())));
        profile.setSpecializations(form.getSpecializations());
        profile.setLocalExperiences(form.getLocalExperiences());
        profile.setDocumentsSubmittedUrl(form.getDocumentsSubmittedUrl());
        profile.setBio(form.getBio());
        if ("NEEDS_INFO".equals(profile.getStatus()) || "REJECTED".equals(profile.getStatus())) {
            profile.setStatus("PENDING");
        }
        profile.setRankingScore(calculateScore(profile));
        return agentRepository.save(profile);
    }

    public List<AgentProfile> getPendingAgents() {
        List<AgentProfile> list = new ArrayList<>(agentRepository.findByStatus("PENDING"));
        list.addAll(agentRepository.findByStatus("NEEDS_INFO"));
        return list;
    }

    public List<AgentProfile> getAllApprovedAgents() {
        return agentRepository.findByStatusOrderByRankingScoreDesc("APPROVED");
    }

    // Returns Top 5 Approved Agents sorted by Ranking Score
    public List<AgentProfile> getTop5ApprovedAgents() {
        return getAllApprovedAgents().stream().limit(5).collect(Collectors.toList());
    }

    /** Approved agents matching a specialization / name keyword (null = all), best ranked first. */
    public List<AgentProfile> searchApprovedAgents(String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return getAllApprovedAgents().stream()
                .filter(a -> kw.isEmpty()
                        || contains(a.getAgencyName(), kw) || contains(a.getSpecializations(), kw)
                        || contains(a.getLocalExperiences(), kw))
                .collect(Collectors.toList());
    }

    private static boolean contains(String text, String kw) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(kw);
    }

    @Transactional
    public void updateAgentAdminStatus(Long agentId, String status, String note) {
        AgentProfile agent = getProfileById(agentId);
        if (agent == null) return;
        agent.setStatus(status);
        agent.setAdminNote(note);
        agentRepository.save(agent);
        String title = "APPROVED".equals(status) ? "Your agent profile is approved"
                : "The administrator needs more information about your agent profile";
        notificationService.notify(agent.getUsername(), title,
                note == null ? "Tourists can now find you and send you trip requests." : note, "/agents/portal");
    }

    /**
     * Fully revokes an agent: clears out their vacation requests first (a VacationRequest
     * has a hard FK to agent_profiles.id), then deletes the profile itself (sample itineraries
     * cascade). Wrapped in one transaction so it's all-or-nothing.
     */
    @Transactional
    public void deleteAgent(Long agentId) {
        vacationRequestRepository.deleteByAgentProfileId(agentId);
        agentRepository.deleteById(agentId);
    }

    // ---------------- sample itineraries (public profile, PBI-24) ----------------

    @Transactional
    public String addSampleItinerary(String username, String title, String description, int days, double cost) {
        AgentProfile profile = getProfileByUsername(username);
        if (profile == null) return "Save your agent profile first.";
        if (title == null || title.isBlank()) return "Give the sample itinerary a title.";
        if (days < 1 || days > 60) return "Duration must be 1-60 days.";
        if (cost < 0) return "Cost cannot be negative.";
        ItineraryPlan plan = new ItineraryPlan();
        plan.setTitle(title.trim());
        plan.setDescription(description == null ? null : description.trim());
        plan.setDurationDays(days);
        plan.setEstimatedCost(cost);
        profile.addItinerary(plan);
        agentRepository.save(profile);
        return null;
    }

    /** @return null when updated, otherwise why not */
    @Transactional
    public String updateSampleItinerary(String username, Long planId, String title, String description, int days, double cost) {
        AgentProfile profile = getProfileByUsername(username);
        ItineraryPlan plan = profile == null ? null : profile.getSampleItineraries().stream()
                .filter(p -> p.getId().equals(planId)).findFirst().orElse(null);
        if (plan == null) return "Sample itinerary not found.";
        if (title == null || title.isBlank()) return "Give the sample itinerary a title.";
        if (days < 1 || days > 60) return "Duration must be 1-60 days.";
        if (cost < 0) return "Cost cannot be negative.";
        plan.setTitle(title.trim());
        plan.setDescription(description == null ? null : description.trim());
        plan.setDurationDays(days);
        plan.setEstimatedCost(cost);
        agentRepository.save(profile);
        return null;
    }

    @Transactional
    public void removeSampleItinerary(String username, Long planId) {
        AgentProfile profile = getProfileByUsername(username);
        if (profile == null) return;
        profile.getSampleItineraries().removeIf(p -> p.getId().equals(planId));
        agentRepository.save(profile);
    }

    // ---------------- vacation requests (PBI-07 / PBI-21 / PBI-23) ----------------

    /** @return null when sent, otherwise why not */
    @Transactional
    public String createVacationRequest(Long agentId, VacationRequest request, String userUsername) {
        AgentProfile agent = getProfileById(agentId);
        if (agent == null || !"APPROVED".equals(agent.getStatus())) return "That travel agent is not available.";
        if (request.getTravelStart() != null && request.getTravelEnd() != null
                && request.getTravelEnd().isBefore(request.getTravelStart())) {
            return "The trip end date must be after the start date.";
        }
        request.setAgentProfile(agent);
        request.setUserUsername(userUsername);
        request.setStatus("PENDING_PROPOSAL");
        request.setCreatedAt(LocalDateTime.now());
        request.setUpdatedAt(LocalDateTime.now());
        vacationRequestRepository.save(request);
        notificationService.notify(agent.getUsername(), "New trip planning request from " + request.getUserName(),
                request.getUserPreferences(), "/agents/portal");
        return null;
    }

    public List<VacationRequest> getRequestsForAgent(String agentUsername) {
        return sortNewest(vacationRequestRepository.findByAgentProfileUsername(agentUsername));
    }

    public List<VacationRequest> getRequestsForUser(String userUsername) {
        return sortNewest(vacationRequestRepository.findByUserUsername(userUsername));
    }

    private List<VacationRequest> sortNewest(List<VacationRequest> list) {
        return list.stream().sorted(Comparator.comparing(
                (VacationRequest r) -> r.getUpdatedAt() != null ? r.getUpdatedAt() : r.getCreatedAt(),
                Comparator.nullsLast(Comparator.reverseOrder()))).collect(Collectors.toList());
    }

    public VacationRequest getVacationRequestById(Long requestId) {
        return requestId == null ? null : vacationRequestRepository.findById(requestId).orElse(null);
    }

    /**
     * The assigned agent sends a new version of the itinerary. Each send is stored as a revision so the
     * tourist sees the change history.
     * @return null when sent, otherwise why not
     */
    @Transactional
    public String submitAgentProposal(Long requestId, String agentUsername, String plan, double price,
                                      String changeNote, List<Attraction> attractions) {
        VacationRequest req = getVacationRequestById(requestId);
        if (req == null || req.getAgentProfile() == null || !agentUsername.equals(req.getAgentProfile().getUsername())) {
            return "That request is not assigned to you.";
        }
        if (!req.isOpenForProposal()) return "This request is already " + req.getStatus().toLowerCase() + ".";
        if (plan == null || plan.isBlank()) return "Write the itinerary plan.";
        if (price < 0) return "Price cannot be negative.";
        if (!req.getRevisions().isEmpty() && (changeNote == null || changeNote.isBlank())) {
            return "Say what changed in this version - the traveller sees it next to the new plan.";
        }

        ItineraryRevision rev = new ItineraryRevision();
        rev.setRequest(req);
        rev.setVersion(req.getRevisions().size() + 1);
        rev.setPlan(plan.trim());
        rev.setPrice(price);
        rev.setChangeNote(changeNote == null || changeNote.isBlank() ? null : changeNote.trim());
        // Keep the traveller's request with the version that answers it, so the history shows both sides
        if ("RE_REQUESTED".equals(req.getStatus())) rev.setRequestedChanges(req.getReRequestFeedback());
        rev.setAttractions(new ArrayList<>(attractions));
        req.getRevisions().add(0, rev);

        req.setProposedItineraryPlan(rev.getPlan());
        req.setProposedPrice(price);
        req.setStatus("PROPOSED");
        req.setUpdatedAt(LocalDateTime.now());
        vacationRequestRepository.save(req);

        notificationService.notify(req.getUserUsername(),
                (rev.getVersion() == 1 ? "Your itinerary is ready - " : "Your itinerary was updated (v" + rev.getVersion() + ") - ")
                        + req.getAgentProfile().getAgencyName(),
                rev.getChangeNote() != null ? rev.getChangeNote() : "Review the plan and accept it or ask for changes.",
                "/agents/explore#my-requests");
        return null;
    }

    /**
     * The tourist accepts, rejects or asks for changes. Only allowed on the tourist's own request while a
     * proposal is waiting for them, so an accepted plan cannot be accepted (and rewarded) twice.
     * @return null on success, otherwise why not
     */
    @Transactional
    public String handleUserDecision(Long requestId, String username, String decision, String feedback) {
        return handleUserDecision(requestId, username, decision, feedback, null);
    }

    /**
     * @param version the itinerary version the traveller was looking at; if the agent has sent a newer one in
     *                the meantime the decision is refused, so nobody accepts (or rejects) a plan they never saw
     */
    @Transactional
    public String handleUserDecision(Long requestId, String username, String decision, String feedback, Integer version) {
        VacationRequest req = getVacationRequestById(requestId);
        if (req == null || !username.equals(req.getUserUsername())) return "That request was not found.";
        if (!"PROPOSED".equals(req.getStatus())) return "There is no proposal waiting for your decision.";
        if (version != null && req.getCurrentRevision() != null && req.getCurrentRevision().getVersion() != version) {
            return "Your agent has just sent a newer version (v" + req.getCurrentRevision().getVersion()
                    + "). Please review it before deciding.";
        }

        AgentProfile agent = req.getAgentProfile();
        String title;
        if ("ACCEPT".equalsIgnoreCase(decision)) {
            req.setStatus("ACCEPTED");
            // REWARD SYSTEM: Boost Agent Performance & Rank!
            agent.setCompletedJobs(agent.getCompletedJobs() + 1);
            agent.setRewardPoints(agent.getRewardPoints() + 10); // Gain +10 points per accepted job
            agent.setRankingScore(calculateScore(agent));
            agentRepository.save(agent);
            title = req.getUserName() + " accepted your itinerary (+10 points)";
        } else if ("RE_REQUEST".equalsIgnoreCase(decision)) {
            if (feedback == null || feedback.isBlank()) return "Tell the agent what you would like changed.";
            req.setStatus("RE_REQUESTED");
            req.setReRequestFeedback(feedback.trim());
            title = req.getUserName() + " asked for changes to the itinerary";
        } else if ("REJECT".equalsIgnoreCase(decision)) {
            req.setStatus("REJECTED");
            title = req.getUserName() + " declined the itinerary";
        } else {
            return "Unknown decision.";
        }
        req.setUpdatedAt(LocalDateTime.now());
        vacationRequestRepository.save(req);
        notificationService.notifyInApp(agent.getUsername(), title,
                feedback == null ? "" : feedback, "/agents/portal");
        return null;
    }

    /**
     * The tourist withdraws a trip request that has not been accepted yet (its plan versions go with it).
     * @return null when withdrawn, otherwise why not
     */
    @Transactional
    public String withdrawRequest(Long requestId, String username) {
        VacationRequest req = getVacationRequestById(requestId);
        if (req == null || !username.equals(req.getUserUsername())) return "That request was not found.";
        if ("ACCEPTED".equals(req.getStatus())) return "An accepted itinerary cannot be withdrawn - contact your agent.";
        String agent = req.getAgentProfile().getUsername();
        vacationRequestRepository.delete(req);
        notificationService.notifyInApp(agent, req.getUserName() + " withdrew their trip request",
                req.getUserPreferences(), "/agents/portal");
        return null;
    }

    // ---------------- metrics ----------------

    /** [pending, proposed, re-requested, accepted, rejected] counts for one agent. */
    public long[] statusCounts(String agentUsername) {
        String[] statuses = {"PENDING_PROPOSAL", "PROPOSED", "RE_REQUESTED", "ACCEPTED", "REJECTED"};
        long[] counts = new long[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            counts[i] = vacationRequestRepository.countByAgentProfileUsernameAndStatus(agentUsername, statuses[i]);
        }
        return counts;
    }

    /**
     * Dynamic Automated Ranking Score Formula:
     * Score = (Completed Jobs * 5.0) + (Reward Points * 1.5) + (Experience Years * 2.0)
     */
    private double calculateScore(AgentProfile agent) {
        double jobsScore = agent.getCompletedJobs() * 5.0;
        double pointsScore = agent.getRewardPoints() * 1.5;
        double expScore = agent.getExperienceYears() * 2.0;
        return jobsScore + pointsScore + expScore;
    }
}
