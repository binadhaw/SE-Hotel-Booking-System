package com.Reservation.Hotel.repository;

import com.Reservation.Hotel.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    List<AppUser> findByRole(String role);

    long countByRole(String role);

    /** Admin search: matches username, full name or email; role filter is optional (null = all). */
    @Query("select u from AppUser u where (:role is null or u.role = :role) and (" +
            ":q is null or lower(u.username) like lower(concat('%', :q, '%')) " +
            "or lower(u.fullName) like lower(concat('%', :q, '%')) " +
            "or lower(u.email) like lower(concat('%', :q, '%'))) order by u.createdAt desc")
    List<AppUser> search(@Param("q") String q, @Param("role") String role);
}
