package com.projectw.domain.waiting.entity;

import com.projectw.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@NoArgsConstructor
public class DailyWaitingStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    private LocalDate date; // 통계 날짜

    private long totalWaitingCount; // 하루 동안 총 대기 인원 수
    private long completedCount; // 완료된 대기 건수
    private long canceledCount; // 취소된 대기 건수


    private double averageWaitingTime; // 하루 평균 대기 시간

    private double cancellationRate; // 취소율 (퍼센트)

    private LocalDateTime createdAt;
}

