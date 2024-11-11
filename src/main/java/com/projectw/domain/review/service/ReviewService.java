package com.projectw.domain.review.service;

import com.projectw.common.config.S3Service;
import com.projectw.domain.like.repository.LikeRepository;
import com.projectw.domain.menu.entity.Menu;
import com.projectw.domain.menu.repository.MenuRepository;
import com.projectw.domain.reservation.entity.Reservation;
import com.projectw.domain.reservation.enums.ReservationStatus;
import com.projectw.domain.reservation.repository.ReservationRepository;
import com.projectw.domain.review.dto.ReviewRequest;
import com.projectw.domain.review.dto.ReviewResponse;
import com.projectw.domain.review.dto.request.ReviewRequestDto;
import com.projectw.domain.review.dto.request.ReviewUpdateDto;
import com.projectw.domain.review.dto.response.ReviewResponseDto;
import com.projectw.domain.review.entity.Review;
import com.projectw.domain.review.entity.ReviewImage;
import com.projectw.domain.review.repository.ReviewRepository;
import com.projectw.domain.user.entity.User;
import com.projectw.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final MenuRepository menuRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final LikeRepository likeRepository;
    private final S3Service s3Service;

    @Transactional
    public ReviewResponse.Info createReview(Long menuId, ReviewRequest.Create reviewRequestDto, String email, List<MultipartFile> images) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다."));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("권한이 없습니다."));

        Reservation reservation = reservationRepository.findByUserAndStore(user, menu.getStore())
                .orElseThrow(() -> new IllegalArgumentException("해당 매장의 예약 내역이 없습니다."));

        if (!reservation.getStatus().equals(ReservationStatus.COMPLETE)) {
            throw new IllegalArgumentException("방문 완료된 예약에 대해서만 리뷰를 작성할 수 있습니다.");
        }

        if (reviewRepository.existsByUserAndMenu(user, menu)) {
            throw new IllegalArgumentException("이미 리뷰를 작성하셨습니다.");
        }

        Review review = Review.builder()
                .title(reviewRequestDto.title())
                .content(reviewRequestDto.content())
                .rating(reviewRequestDto.rating())
                .reservation(reservation)
                .build();

        processImages(images, review);
        Review savedReview = reviewRepository.save(review);
        return new ReviewResponse.Info(savedReview, user);
    }

    public Page<ReviewResponse.Info> getMenuReviews(Long menuId, Pageable pageable) {
        Menu menu = menuRepository.findById(menuId)
                .orElseThrow(() -> new IllegalArgumentException("메뉴를 찾을 수 없습니다."));

        Page<Object[]> reviewsWithCount = reviewRepository.findAllByMenuWithUserAndLikeCount(menu, pageable);

        return reviewsWithCount.map(objects -> {
            Review review = (Review) objects[0];
            Long likeCount = (Long) objects[1];
            User user = review.getUser();

            return new ReviewResponse.Info(review, user);
        });
    }

    @Transactional
    public ReviewResponse.Info updateReview(Long reviewId, ReviewRequest.Update updateDto, String email, List<MultipartFile> images) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!review.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("본인의 리뷰만 수정할 수 있습니다.");
        }

        // 이미지 삭제 처리
        if (updateDto.deleteImageIds() != null && !updateDto.deleteImageIds().isEmpty()) {
            review.getImages().stream()
                    .filter(image -> updateDto.deleteImageIds().contains(image.getId()))
                    .forEach(image -> {
                        try {
                            deleteImage(image);
                        } catch (Exception e) {
                            log.error("이미지 삭제 실패: {}", image.getImageUrl(), e);
                        }
                    });
            review.getImages().removeIf(image ->
                    updateDto.deleteImageIds().contains(image.getId()));
        }

        // 새로운 이미지 추가 처리
        if (images != null && !images.isEmpty()) {
            processImages(images, review);
        }

        // 리뷰 내용과 평점 업데이트
        review.update(updateDto.content(), updateDto.rating());

        Long likeCount = likeRepository.countByReview(review);
        boolean liked = likeRepository.existsByReviewAndUser(review, user);

        return new ReviewResponse.Info(review, user);  // ReviewResponse.Info로 반환
    }

    @Transactional
    public ReviewResponse.Info deleteReview(Long reviewId, String email) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("리뷰를 찾을 수 없습니다."));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("삭제 권한이 없습니다."));

        // 리뷰 이미지 삭제
        review.getImages().forEach(this::deleteImage);

        reviewRepository.delete(review);
        Long likeCount = likeRepository.countByReview(review);
        boolean liked = likeRepository.existsByReviewAndUser(review, user);

        return new ReviewResponse.Info(review, user);
    }

    @Transactional
    public void processImages(List<MultipartFile> images, Review review) {
        if (images == null || images.isEmpty()) return;

        for (MultipartFile image : images) {
            String imageUrl = s3Service.uploadFile(image);
            ReviewImage reviewImage = ReviewImage.builder()
                    .imageUrl(imageUrl)
                    .review(review)
                    .build();
            review.addImage(reviewImage);
        }
    }

    private void deleteImage(ReviewImage image) {
        try {
            String fileName = image.getImageUrl().substring(image.getImageUrl().lastIndexOf("/") + 1);
            s3Service.deleteFile(fileName);
        } catch (Exception e) {
            log.error("S3 이미지 삭제 실패: {}", image.getImageUrl(), e);
            throw new RuntimeException("이미지 삭제 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    @Transactional
    public Page<ReviewResponse.Info> getUserReviews(String email, Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        Page<Review> reviews = reviewRepository.findAllByUserOrderByCreatedAtDesc(user, pageable);

        return reviews.map(review -> {
            // 좋아요 수 계산
            Long likeCount = likeRepository.countByReview(review);
            // 현재 사용자의 좋아요 여부 확인
            boolean liked = likeRepository.existsByReviewAndUser(review, user);

            return new ReviewResponse.Info(review, user);
        });
    }
}