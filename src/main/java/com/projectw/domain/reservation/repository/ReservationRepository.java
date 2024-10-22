package com.projectw.domain.reservation.repository;

import com.projectw.domain.reservation.entity.Reservation;
import com.projectw.domain.reservation.enums.ReservationStatus;
import com.projectw.domain.reservation.enums.ReservationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, ReservationDslRepository {
    boolean existsByUserIdAndStoreIdAndTypeAndStatus(Long user_id, Long store_id, ReservationType type, ReservationStatus status);

    @Query("SELECT IFNULL(MAX(r.reservationNo) + 1,1) FROM Reservation r WHERE r.type = :type AND r.reservationDate = :date")
    long findMaxReservationDate(@Param("type") ReservationType type , @Param("date") LocalDate date);

    @Query("SELECT r FROM Reservation r INNER JOIN FETCH r.store s INNER JOIN FETCH s.user u WHERE r.id = :id")
    Optional<Reservation> findReservationById(@Param("id") Long id);
}
