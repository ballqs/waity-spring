package com.projectw.domain.review.dto.response;

import com.projectw.domain.review.entity.Review;
import com.projectw.domain.review.entity.ReviewImage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Getter;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class ReviewResponseDto {
    private Long id;
    private String title;
    private String content;
    private List<String> imageUrls;
    private int rating;
    private String username;
    private String storeName;
    private String menuName;
    private Long likeCount;     // 좋아요 수
    private boolean liked;      // 현재 사용자의 좋아요 여부
    private LocalDateTime reservationDate;
    private LocalDateTime createdAt;

    public static ReviewResponseDto from(Review review, Long likeCount, boolean liked) {
        return ReviewResponseDto.builder()
                .id(review.getId())
                .content(review.getContent())
                .rating(review.getRating())
                .username(review.getReservation().getUser().getNickname())
                .storeName(review.getReservation().getStore().getTitle())
                .reservationDate(review.getReservation().getCreatedAt())
                .menuName(review.getReservation().getStore().getMenu().toString())
                .createdAt(review.getCreatedAt())
                .liked(liked)
                .likeCount(likeCount)
                .imageUrls(review.getImages().stream()
                        .map(ReviewImage::getImageUrl)
                        .collect(Collectors.toList()))

                .build();
    }

}
