package com.projectw.domain.reservation.controller;

import com.projectw.common.dto.SuccessResponse;
import com.projectw.domain.reservation.dto.ReserveRequest;
import com.projectw.domain.reservation.dto.ReserveResponse;
import com.projectw.domain.reservation.service.ReservationService;
import com.projectw.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v2")
public class CartController {

    private final ReservationService reservationService;

    /**
     * method : addCartItem
     * memo   : 유저가 해당 음식점에서 메뉴를 골라서 담는 작업
     * */
    @PostMapping("/store/{storeId}/cart")
    public ResponseEntity<SuccessResponse<Void>> addCartItem(
            @AuthenticationPrincipal AuthUser authUser ,
            @PathVariable Long storeId,
            @Valid @RequestBody ReserveRequest.AddCart addCart) {
        reservationService.addCartItem(authUser.getUserId() , storeId , addCart);
        return ResponseEntity.ok(SuccessResponse.of(null));
    }

    /**
     * method : updateCartItem
     * memo   : 유저가 해당 음식점에서 메뉴를 골라서 담는 작업
     * */
    @PatchMapping("/store/{storeId}/cart")
    public ResponseEntity<SuccessResponse<Void>> updateCartItem(
            @AuthenticationPrincipal AuthUser authUser ,
            @PathVariable Long storeId,
            @Valid @RequestBody ReserveRequest.UpdateCart updateCart) {
        reservationService.updateCartItem(authUser.getUserId() , storeId , updateCart);
        return ResponseEntity.ok(SuccessResponse.of(null));
    }

    /**
     * method : removeCartItem
     * memo   : 유저가 해당 음식점에서 메뉴를 삭제하는 작업
     * */
    @DeleteMapping("/store/{storeId}/cart")
    public ResponseEntity<SuccessResponse<Void>> removeCartItem(
            @AuthenticationPrincipal AuthUser authUser ,
            @PathVariable Long storeId,
            @Valid @RequestBody ReserveRequest.RemoveCart removeCart) {
        reservationService.removeCartItem(authUser.getUserId() , storeId , removeCart);
        return ResponseEntity.ok(SuccessResponse.of(null));
    }

    /**
     * method : getCartItems
     * memo   : 유저가 해당 음식점에서 메뉴를 조회하는 작업
     * */
    @GetMapping("/store/{storeId}/cart")
    public ResponseEntity<SuccessResponse<List<ReserveResponse.Carts>>> getCartItems(
            @AuthenticationPrincipal AuthUser authUser ,
            @PathVariable Long storeId) {
        return ResponseEntity.ok(SuccessResponse.of(reservationService.getCartItems(authUser.getUserId() , storeId)));
    }
}
