package com.projectw.domain.reservation.service;

import com.projectw.common.annotations.RedisListener;
import com.projectw.common.enums.ResponseCode;
import com.projectw.common.exceptions.ForbiddenException;
import com.projectw.common.exceptions.NotFoundException;
import com.projectw.common.utils.Scheduler;
import com.projectw.domain.menu.entity.Menu;
import com.projectw.domain.menu.repository.MenuRepository;
import com.projectw.domain.payment.event.PaymentCancelEvent;
import com.projectw.domain.payment.event.PaymentTimeoutCancelEvent;
import com.projectw.domain.reservation.component.ReservationCheckService;
import com.projectw.domain.reservation.dto.ReserveMenuRequest;
import com.projectw.domain.reservation.dto.ReserveRedis;
import com.projectw.domain.reservation.dto.ReserveRequest;
import com.projectw.domain.reservation.dto.ReserveResponse;
import com.projectw.domain.reservation.entity.Reservation;
import com.projectw.domain.reservation.entity.ReservationMenu;
import com.projectw.domain.reservation.enums.ReservationStatus;
import com.projectw.domain.reservation.enums.ReservationType;
import com.projectw.domain.reservation.exception.InvalidCartException;
import com.projectw.domain.reservation.repository.ReservationMenuRepository;
import com.projectw.domain.reservation.repository.ReservationRepository;
import com.projectw.domain.store.entity.Store;
import com.projectw.domain.store.repository.StoreRepository;
import com.projectw.domain.user.entity.User;
import com.projectw.domain.user.repository.UserRepository;
import com.projectw.domain.waiting.dto.WaitingPoll;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RSetMultimap;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Slf4j
@Service
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationMenuRepository reservationMenuRepository;
    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final MenuRepository menuRepository;

    private final ReservationCheckService reservationCheckService;

    private final ApplicationEventPublisher eventPublisher;
    private final Scheduler scheduler;
    private final RedisTemplate<String , Object> redisTemplate;
    private final RedissonClient redissonClient;

    @Transactional
    public void prepareReservation(ReserveRequest.InsertReservation insertReservation) {
        // 예약번호 채번하기
        LocalDate now = LocalDate.now();
        Long reservationNo = reservationRepository.findMaxReservationDate(ReservationType.RESERVATION , now);

        // 장바구니에 메뉴가 담겨있는지 검증
        //insertReservation.user().getId()
        String key = assembleCartRedisKey(insertReservation.store().getId());
        RSetMultimap<Long, ReserveRedis.Menu> rSetMultiMap = redissonClient.getSetMultimap(key);
        RSet<ReserveRedis.Menu> rSet = rSetMultiMap.get(insertReservation.user().getId());
        boolean menuYN = !rSet.isEmpty();

        // 예약 Entity 만들기
        Reservation reservation = Reservation.builder()
                .orderId(insertReservation.orderId())
                .status(ReservationStatus.RESERVATION)      // 예약 단계!!(승인 안된 상태!)
                .type(ReservationType.RESERVATION)          // 웨이팅 , 예약 중 예약이라는 의미
                .reservationDate(insertReservation.reservationDate())
                .reservationTime(insertReservation.reservationTime())
                .numberPeople(insertReservation.numberPeople())
                .reservationNo(reservationNo)
                .menuYN(menuYN)
                .paymentYN(false)
                .paymentAmt(insertReservation.paymentAmt())
                .user(insertReservation.user())
                .store(insertReservation.store())
                .build();

        Reservation saveReservation = reservationRepository.save(reservation);

        // menu 값이 있을때
        if (menuYN) {
            List<ReservationMenu> reservationMenus = new ArrayList<>();
            for (ReserveRedis.Menu menu : rSet) {
                Menu m = menuRepository.findById(menu.menuId()).orElseThrow(() -> new NotFoundException(ResponseCode.NOT_FOUND_MENU));
                ReservationMenu reservationMenu = new ReservationMenu(m , menu.menuName() , menu.price(), menu.menuCnt() , saveReservation);
                reservationMenus.add(reservationMenu);
                rSetMultiMap.remove(insertReservation.user().getId() , menu);
            }
            reservationMenuRepository.saveAll(reservationMenus);
        }

        // 지정한 시간 후에 자동 실행!!
        scheduler.scheduleOnceAfterDelay(10 , TimeUnit.MINUTES , this::autoCancelMethod, saveReservation.getId());
    }

    // 결제 완료
    @Transactional
    public void successReservation(String orderId) {
        Reservation reservation = reservationRepository.findByOrderId(orderId).orElseThrow(() -> new NotFoundException(ResponseCode.NOT_FOUND_RESERVATION));
        reservation.updatePaymentYN(true);
    }

    @Transactional
    public void cancelReservation(Long userId , Long storeId , Long reservationId , ReserveRequest.Cancel cancel) {
        // 예약 어떤지?
        Reservation reservation = reservationRepository.findByIdAndStoreId(reservationId , storeId).orElseThrow(() -> new NotFoundException(ResponseCode.NOT_FOUND_RESERVATION));

        // 본인 예약건인지?
        reservationCheckService.isUserReservation(userId , reservation);

        // 예약을 취소할수 있는 타입인지 검증
        reservationCheckService.canChangeReservationType(reservation , ReservationType.RESERVATION);

        switch (reservation.getStatus()) {
            case RESERVATION:
            case APPLY:
                reservation.updateStatus(ReservationStatus.CANCEL);

                if (reservation.isPaymentYN()) {
                    // PaymentEventListener 결제취소
                    PaymentCancelEvent paymentCancelEvent = new PaymentCancelEvent(reservation.getOrderId() , cancel.cancelReason());
                    eventPublisher.publishEvent(paymentCancelEvent);
                }
                break;
            default:
                throw new ForbiddenException(ResponseCode.CANCEL_FORBIDDEN);
        }
    }

    public Page<ReserveResponse.Infos> getUserReservations(Long userId , ReserveRequest.Parameter parameter) {
        Pageable pageable = PageRequest.of(parameter.page() - 1, parameter.size());
        return reservationRepository.getUserReservations(userId , parameter , pageable);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoCancelMethod(Long reservationId) {
        log.info("autoCancelMethod 접근!!");
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow(() -> new NotFoundException(ResponseCode.NOT_FOUND_RESERVATION));

        if (!reservation.isPaymentYN()) {
            // 예약건 취소
            reservation.updateStatus(ReservationStatus.CANCEL);
            reservationRepository.save(reservation);
            // 결제건 취소
            eventPublisher.publishEvent(new PaymentTimeoutCancelEvent(reservation.getOrderId()));
        }
    }

    public void addCartItem(Long userId , Long storeId , ReserveRequest.AddCart addCart) {

        // key 생성!
        String key = assembleCartRedisKey(storeId);
        RSetMultimap<Long, ReserveRedis.Menu> rSetMultiMap = redissonClient.getSetMultimap(key); // 대기 정보 저장용 멀티맵

        List<ReserveMenuRequest.Menu> menus = addCart.menus();

        // redis에 담기
        for (ReserveMenuRequest.Menu menu : menus) {
            ReserveRedis.Menu reserveRedisMenu = new ReserveRedis.Menu(menu.menuIds(), menu.menuName(), menu.price(), menu.menuCnt());
            // 중복 체크
            boolean exists = false;
            for (ReserveRedis.Menu existingMenu : rSetMultiMap.get(userId)) {
                if (existingMenu.menuId().equals(reserveRedisMenu.menuId())) {
                    exists = true; // 중복된 메뉴 ID가 발견됨
                    break;
                }
            }
            // 중복되지 않을 경우에만 추가
            if (!exists) {
                rSetMultiMap.put(userId, reserveRedisMenu);
            }
        }
        redisTemplate.expire(key , 24 , TimeUnit.HOURS);
    }

    public void updateCartItem(Long userId , Long storeId , ReserveRequest.UpdateCart updateCart) {
        // key 생성!
        String key = assembleCartRedisKey(storeId);
        RSetMultimap<Long, ReserveRedis.Menu> rSetMultiMap = redissonClient.getSetMultimap(key);

        // 해당 메뉴 ID가 존재하는지 확인
        boolean menuExists = false;
        for (ReserveRedis.Menu menu : rSetMultiMap.get(userId)) {
            if (menu.menuId().equals(updateCart.menuId())) {
                rSetMultiMap.remove(userId, menu);
                ReserveRedis.Menu updatedMenu  = menu.updateCnt(updateCart.menuCnt());
                rSetMultiMap.put(userId, updatedMenu);
                menuExists = true;
                break;
            }
        }

        // 메뉴가 존재하지 않을 경우 예외 처리
        if (!menuExists) {
            throw new InvalidCartException(ResponseCode.INVALID_CART); // 메뉴를 찾을 수 없음 예외
        }
    }

    public void removeCartItem(Long userId , Long storeId , ReserveRequest.RemoveCart removeCart) {
        String key = assembleCartRedisKey(storeId);
        RSetMultimap<Long, ReserveRedis.Menu> rSetMultiMap = redissonClient.getSetMultimap(key);
        for (ReserveRedis.Menu menu : rSetMultiMap.get(userId)) {
            if (menu.menuId().equals(removeCart.menuId())) {
                rSetMultiMap.remove(userId , menu);
                break;
            }
        }
    }

    public List<ReserveResponse.Carts> getCartItems(Long userId , Long storeId) {
        String key = assembleCartRedisKey(storeId);
        RSetMultimap<Long, ReserveRedis.Menu> rSetMultiMap = redissonClient.getSetMultimap(key);

        List<ReserveResponse.Carts> carts = new ArrayList<>();
        for (ReserveRedis.Menu menu : rSetMultiMap.get(userId)) {
            ReserveResponse.Carts cart = new ReserveResponse.Carts(menu.menuId() , menu.menuName() , menu.price() , menu.menuCnt());
            carts.add(cart);
        }

        return carts;
    }

    private String assembleCartRedisKey(Long storeId) {
        return "store:"+storeId;
    }

    @Transactional
    @RedisListener(topic = "waiting-poll")
    public void onWaitingPoll(WaitingPoll waitingPoll){
        LocalDateTime at = waitingPoll.createdAt();
        Long userId = waitingPoll.userId();
        Long storeId = waitingPoll.storeId();
        Long num = waitingPoll.waitingNum();

        User user = userRepository.findById(userId).orElseThrow(()-> new NotFoundException(ResponseCode.NOT_FOUND_USER));
        Store store = storeRepository.findById(storeId).orElseThrow(()-> new NotFoundException(ResponseCode.NOT_FOUND_STORE));

        Reservation reservation = Reservation.builder()
                .status(ReservationStatus.APPLY)
                .type(ReservationType.WAIT)
                .user(user)
                .store(store)
                .reservationDate(at.toLocalDate())
                .reservationTime(at.toLocalTime().truncatedTo(TimeUnit.SECONDS.toChronoUnit()))
                .numberPeople(1L)
                .reservationNo(num)
                .menuYN(false)
                .build();

        reservationRepository.save(reservation);
    }
}
