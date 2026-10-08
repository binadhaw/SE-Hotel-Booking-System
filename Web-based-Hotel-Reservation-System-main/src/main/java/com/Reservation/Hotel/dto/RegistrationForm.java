package com.Reservation.Hotel.dto;

import jakarta.validation.constraints.*;

/** Fields posted by the sign-up page. Kept separate from AppUser so nobody can post a role/enabled flag directly. */
public class RegistrationForm {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 30, message = "Username must be 3-30 characters")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Only letters, numbers, dot, dash and underscore")
    private String username;

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name is too long")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;

    @Pattern(regexp = "^$|^[+0-9 ()-]{7,20}$", message = "Enter a valid phone number")
    private String phone;

    @NotBlank(message = "Please choose an account type")
    private String role = "USER";

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 64, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain letters and numbers")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    @NotBlank(message = "Choose a security question")
    private String securityQuestion;

    @NotBlank(message = "Security answer is required")
    @Size(max = 100, message = "Answer is too long")
    private String securityAnswer;

    // Consent: the privacy notice must be accepted; promotional emails are opt-in (unticked by default)
    @AssertTrue(message = "Please read and accept the privacy notice to create an account")
    private boolean acceptPrivacy;

    private boolean promoAlerts;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public String getSecurityQuestion() { return securityQuestion; }
    public void setSecurityQuestion(String securityQuestion) { this.securityQuestion = securityQuestion; }

    public String getSecurityAnswer() { return securityAnswer; }
    public void setSecurityAnswer(String securityAnswer) { this.securityAnswer = securityAnswer; }

    public boolean isAcceptPrivacy() { return acceptPrivacy; }
    public void setAcceptPrivacy(boolean acceptPrivacy) { this.acceptPrivacy = acceptPrivacy; }

    public boolean isPromoAlerts() { return promoAlerts; }
    public void setPromoAlerts(boolean promoAlerts) { this.promoAlerts = promoAlerts; }
}
