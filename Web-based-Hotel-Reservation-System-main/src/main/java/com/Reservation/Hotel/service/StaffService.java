package com.Reservation.Hotel.service;

import com.Reservation.Hotel.model.Hotel;
import com.Reservation.Hotel.model.StaffMember;
import com.Reservation.Hotel.repository.StaffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class StaffService {

    private final StaffRepository staffRepository;

    public StaffService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    public List<StaffMember> forHotel(Long hotelId) {
        return staffRepository.findByHotelIdOrderByActiveDescFullNameAsc(hotelId);
    }

    public long activeCount(Long hotelId) {
        return staffRepository.countByHotelIdAndActiveTrue(hotelId);
    }

    public StaffMember getById(Long id) {
        return id == null ? null : staffRepository.findById(id).orElse(null);
    }

    /** Business rules beyond the field annotations. @return a message, or null when fine */
    public String problem(StaffMember form) {
        if (form.getRole() != null && !StaffMember.ROLES.contains(form.getRole())) return "Choose a role from the list.";
        if (form.getShift() != null && !StaffMember.SHIFTS.contains(form.getShift())) return "Choose a shift from the list.";
        if (form.getStartDate() != null && (form.getStartDate().isBefore(LocalDate.of(1950, 1, 1))
                || form.getStartDate().isAfter(LocalDate.now().plusYears(1)))) {
            return "Start date must be within the last decades and not more than a year ahead.";
        }
        return null;
    }

    @Transactional
    public StaffMember add(Hotel hotel, StaffMember form) {
        form.setId(null);
        form.setHotel(hotel);
        return staffRepository.save(form);
    }

    @Transactional
    public void update(StaffMember existing, StaffMember form) {
        existing.setFullName(form.getFullName().trim());
        existing.setRole(form.getRole());
        existing.setEmail(form.getEmail());
        existing.setPhone(form.getPhone());
        existing.setShift(form.getShift());
        existing.setStartDate(form.getStartDate());
        existing.setActive(form.isActive());
        existing.setNotes(form.getNotes());
        staffRepository.save(existing);
    }

    @Transactional
    public void delete(StaffMember staff) {
        staffRepository.delete(staff);
    }

    @Transactional
    public void deleteAllForHotel(Long hotelId) {
        staffRepository.deleteByHotelId(hotelId);
    }
}
