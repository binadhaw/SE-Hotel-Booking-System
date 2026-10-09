package com.Reservation.Hotel.service;

import com.Reservation.Hotel.dto.RegistrationForm;
import com.Reservation.Hotel.model.AppUser;
import com.Reservation.Hotel.repository.AppUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserService {

    /** Questions offered on the sign-up page (T-03.2: user selects a pre-set security question). */
    public static final List<String> SECURITY_QUESTIONS = List.of(
            "What was the name of your first pet?",
            "In which city were you born?",
            "What is your mother's maiden name?",
            "What was the name of your first school?",
            "What is your favourite food?"
    );

    /** Roles a visitor may choose when signing up. ADMIN accounts are only created by the seeder. */
    public static final List<String> SELF_REGISTER_ROLES = List.of(
            AppUser.ROLE_USER, AppUser.ROLE_MANAGER, AppUser.ROLE_AGENT);

    private static final Pattern PASSWORD_RULE = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,64}$");

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------------- lookups ----------------

    public Optional<AppUser> findByUsername(String username) {
        if (username == null) return Optional.empty();
        return userRepository.findByUsernameIgnoreCase(username.trim());
    }

    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }

    /** Email for a username, or null when the user has none / does not exist. */
    public String emailOf(String username) {
        return findByUsername(username).map(AppUser::getEmail).orElse(null);
    }

    /** Full name for a username, falling back to the username itself. */
    public String displayNameOf(String username) {
        return findByUsername(username).map(AppUser::getFullName).orElse(username);
    }

    public List<AppUser> search(String q, String role) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        String roleFilter = (role == null || role.isBlank() || "ALL".equalsIgnoreCase(role)) ? null : role.toUpperCase(Locale.ROOT);
        return userRepository.search(query, roleFilter);
    }

    public List<AppUser> findByRole(String role) {
        return userRepository.findByRole(role);
    }

    public long countByRole(String role) {
        return userRepository.countByRole(role);
    }

    public long countAll() {
        return userRepository.count();
    }

    // ---------------- registration ----------------

    /**
     * Validation that needs the database or crosses fields (bean validation handles the rest).
     * @return an error message, or null when the form can be saved
     */
    public String checkRegistration(RegistrationForm form) {
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            return "Passwords do not match.";
        }
        if (!SELF_REGISTER_ROLES.contains(form.getRole())) {
            return "Please choose a valid account type.";
        }
        if (!SECURITY_QUESTIONS.contains(form.getSecurityQuestion())) {
            return "Please choose one of the listed security questions.";
        }
        if (userRepository.existsByUsernameIgnoreCase(form.getUsername().trim())) {
            return "That username is already taken.";
        }
        if (userRepository.existsByEmailIgnoreCase(form.getEmail().trim())) {
            return "An account with that email already exists.";
        }
        return null;
    }

    @Transactional
    public AppUser register(RegistrationForm form) {
        AppUser user = new AppUser();
        user.setUsername(form.getUsername().trim());
        user.setFullName(form.getFullName().trim());
        user.setEmail(form.getEmail().trim().toLowerCase(Locale.ROOT));
        user.setPhone(form.getPhone() == null ? null : form.getPhone().trim());
        user.setRole(form.getRole());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setSecurityQuestion(form.getSecurityQuestion());
        user.setSecurityAnswer(passwordEncoder.encode(normalizeAnswer(form.getSecurityAnswer())));
        user.setPromoAlerts(form.isPromoAlerts()); // only if the user ticked the box (explicit consent)
        user.setEnabled(true);
        return userRepository.save(user);
    }

    /** Used by the data seeder; does nothing when the username already exists. */
    @Transactional
    public void createIfMissing(String username, String rawPassword, String fullName, String email, String role) {
        if (userRepository.existsByUsernameIgnoreCase(username)) return;
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setFullName(fullName);
        user.setEmail(email);
        user.setRole(role);
        user.setSecurityQuestion(SECURITY_QUESTIONS.get(0));
        user.setSecurityAnswer(passwordEncoder.encode(normalizeAnswer("demo")));
        userRepository.save(user);
    }

    // ---------------- profile ----------------

    /** @return an error message, or null on success */
    @Transactional
    public String updateProfile(String username, String fullName, String email, String phone, boolean promoAlerts) {
        AppUser user = findByUsername(username).orElse(null);
        if (user == null) return "Account not found.";
        if (fullName == null || fullName.isBlank()) return "Full name is required.";
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) return "Enter a valid email address.";
        if (phone != null && !phone.isBlank() && !phone.matches("^[+0-9 ()-]{7,20}$")) return "Enter a valid phone number.";

        String cleanEmail = email.trim().toLowerCase(Locale.ROOT);
        Optional<AppUser> emailOwner = userRepository.findByEmailIgnoreCase(cleanEmail);
        if (emailOwner.isPresent() && !emailOwner.get().getId().equals(user.getId())) {
            return "Another account already uses that email.";
        }
        user.setFullName(fullName.trim());
        user.setEmail(cleanEmail);
        user.setPhone(phone == null ? null : phone.trim());
        user.setPromoAlerts(promoAlerts);
        userRepository.save(user);
        return null;
    }

    /** @return an error message, or null on success */
    @Transactional
    public String changePassword(String username, String currentPassword, String newPassword, String confirmPassword) {
        AppUser user = findByUsername(username).orElse(null);
        if (user == null) return "Account not found.";
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            return "Your current password is incorrect.";
        }
        String problem = checkNewPassword(newPassword, confirmPassword);
        if (problem != null) return problem;
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return null;
    }

    // ---------------- travel preferences (Phase 13.3) ----------------

    /** @return an error message, or null on success */
    @Transactional
    public String updatePreferences(String username, List<String> interests, String destination, Double budget) {
        AppUser user = findByUsername(username).orElse(null);
        if (user == null) return "Account not found.";
        List<String> clean = interests == null ? List.of() : interests.stream()
                .filter(com.Reservation.Hotel.model.Attraction.CATEGORIES::contains).distinct().toList();
        if (destination != null && destination.trim().length() > 80) return "Destination must be 80 characters or fewer.";
        if (budget != null && (budget < 1 || budget > 100000)) return "Budget per night must be between $1 and $100,000.";
        user.setTravelInterests(clean.isEmpty() ? null : String.join(",", clean));
        user.setPreferredDestination(destination == null || destination.isBlank() ? null : destination.trim());
        user.setBudgetPerNight(budget);
        userRepository.save(user);
        return null;
    }

    // ---------------- forgot password ----------------

    public boolean checkSecurityAnswer(AppUser user, String answer) {
        return user != null && user.getSecurityAnswer() != null && answer != null
                && passwordEncoder.matches(normalizeAnswer(answer), user.getSecurityAnswer());
    }

    /** @return an error message, or null when the password was reset */
    @Transactional
    public String resetPassword(String username, String answer, String newPassword, String confirmPassword) {
        AppUser user = findByUsername(username).orElse(null);
        if (user == null || !checkSecurityAnswer(user, answer)) {
            return "The answer to your security question is incorrect.";
        }
        String problem = checkNewPassword(newPassword, confirmPassword);
        if (problem != null) return problem;
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        return null;
    }

    // ---------------- admin ----------------

    /** @return an error message, or null on success */
    @Transactional
    public String setEnabled(Long userId, boolean enabled, String actingAdmin) {
        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null) return "User not found.";
        if (user.getUsername().equalsIgnoreCase(actingAdmin)) return "You cannot suspend your own account.";
        if (AppUser.ROLE_ADMIN.equals(user.getRole()) && !enabled) return "Administrator accounts cannot be suspended.";
        user.setEnabled(enabled);
        userRepository.save(user);
        return null;
    }

    /** @return an error message, or null on success */
    @Transactional
    public String deleteUser(Long userId, String actingAdmin) {
        AppUser user = userRepository.findById(userId).orElse(null);
        if (user == null) return "User not found.";
        if (user.getUsername().equalsIgnoreCase(actingAdmin)) return "You cannot delete your own account.";
        if (AppUser.ROLE_ADMIN.equals(user.getRole())) return "Administrator accounts cannot be deleted.";
        userRepository.delete(user);
        return null;
    }

    @Transactional
    public void recordLogin(String username) {
        findByUsername(username).ifPresent(u -> {
            u.setLastLoginAt(LocalDateTime.now());
            userRepository.save(u);
        });
    }

    // ---------------- helpers ----------------

    private String checkNewPassword(String newPassword, String confirmPassword) {
        if (newPassword == null || !PASSWORD_RULE.matcher(newPassword).matches()) {
            return "New password must be 8-64 characters and contain letters and numbers.";
        }
        if (!newPassword.equals(confirmPassword)) {
            return "New passwords do not match.";
        }
        return null;
    }

    private String normalizeAnswer(String answer) {
        return answer == null ? "" : answer.trim().toLowerCase(Locale.ROOT);
    }
}
