package com.palavecino.backend.patient;

import com.palavecino.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "patient")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String phone;

    // Nullable: a guest patient (booked by staff, no account) has no linked user - identified
    // instead by the guest* fields below. Enforced together by chk_patient_user_or_guest.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled;

    @Column(name = "guest_name")
    private String guestName;

    @Column(name = "guest_phone")
    private String guestPhone;

    @Column(name = "guest_email")
    private String guestEmail;

    protected Patient() {
    }

    public Patient(String firstName, String lastName, String phone, User user) {
        this(firstName, lastName, phone, user, true);
    }

    public Patient(String firstName, String lastName, String phone, User user, boolean notificationsEnabled) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.user = user;
        this.notificationsEnabled = notificationsEnabled;
    }

    /**
     * Guest patient: booked by staff on behalf of someone with no account. Notifications default
     * to enabled since there is no preference to opt out of - the only gate on the confirmation
     * email is whether a guest email was actually provided (see {@link #getEmail()}).
     */
    public static Patient guest(String guestName, String guestPhone, String guestEmail) {
        Patient patient = new Patient();
        String[] nameParts = guestName.trim().split("\\s+", 2);
        patient.firstName = nameParts[0];
        patient.lastName = nameParts.length > 1 ? nameParts[1] : "";
        patient.phone = guestPhone;
        patient.notificationsEnabled = true;
        patient.guestName = guestName;
        patient.guestPhone = guestPhone;
        patient.guestEmail = guestEmail;
        return patient;
    }

    /**
     * Resolves the address to notify this patient at, regardless of whether they're registered
     * (email lives on their account) or a guest (email, if any, lives on this row). Centralizes
     * the null-check that every appointment-email call site would otherwise have to repeat.
     */
    public String getEmail() {
        return user != null ? user.getEmail() : guestEmail;
    }

    public boolean isGuest() {
        return user == null;
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public String getGuestName() {
        return guestName;
    }

    public String getGuestPhone() {
        return guestPhone;
    }

    public String getGuestEmail() {
        return guestEmail;
    }
}
