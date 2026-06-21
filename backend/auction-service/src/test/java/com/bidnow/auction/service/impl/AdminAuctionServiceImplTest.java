package com.bidnow.auction.service.impl;

import com.bidnow.auction.domain.entity.AuctionCategory;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.request.AdminAuctionFilterRequest;
import com.bidnow.auction.dto.request.AdminAuctionReasonRequest;
import com.bidnow.auction.dto.response.AdminAuctionSummaryResponse;
import com.bidnow.auction.dto.response.SellerAuctionResponse;
import com.bidnow.auction.feign.UserServiceClient;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.mapper.AuctionMapper;
import com.bidnow.auction.repository.AuctionImageRepository;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionRejectedEvent;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuctionServiceImplTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private AuctionImageRepository auctionImageRepository;
    @Mock
    private AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    @Mock
    private AuctionMapper auctionMapper;
    @Mock
    private AuctionKafkaProducer auctionKafkaProducer;
    @Mock
    private UserServiceClient userServiceClient;
    @InjectMocks
    private AdminAuctionServiceImpl adminAuctionService;

    private AuctionItem buildItem(UUID id, AuctionStatus status) {
        AuctionCategory category = AuctionCategory.builder()
                .id(UUID.randomUUID())
                .name("Electronics")
                .slug("electronics")
                .build();
        return AuctionItem.builder()
                .id(id)
                .sellerId(UUID.randomUUID())
                .title("Vintage Watch")
                .status(status)
                .currentPrice(new BigDecimal("250.00"))
                .startingPrice(new BigDecimal("100.00"))
                .totalBids(3)
                .currentWinnerId(UUID.randomUUID())
                .category(category)
                .build();
    }

    // -------------------------------------------------------
    // listAuctions
    // -------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void listAuctions_happyPath_returnsMappedPage() {
        AuctionItem item = buildItem(UUID.randomUUID(), AuctionStatus.ACTIVE);
        when(auctionItemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(userServiceClient.getUserSummary(any()))
                .thenReturn(BaseResponse.success(UserSummaryResponse.builder().name("Alice").build()));
        when(auctionMapper.toAdminSummaryResponse(eq(item), eq("Alice")))
                .thenReturn(AdminAuctionSummaryResponse.builder().id(item.getId()).build());

        PageResponse<AdminAuctionSummaryResponse> result =
                adminAuctionService.listAuctions(AdminAuctionFilterRequest.builder().build());

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getId()).isEqualTo(item.getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAuctions_sellerServiceUnavailable_fallsBackToNullName() {
        AuctionItem item = buildItem(UUID.randomUUID(), AuctionStatus.ACTIVE);
        when(auctionItemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item)));
        when(userServiceClient.getUserSummary(any())).thenThrow(new RuntimeException("down"));

        adminAuctionService.listAuctions(AdminAuctionFilterRequest.builder().build());

        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(auctionMapper).toAdminSummaryResponse(eq(item), nameCaptor.capture());
        assertThat(nameCaptor.getValue()).isNull();
    }

    // -------------------------------------------------------
    // getAuctionDetail
    // -------------------------------------------------------

    @Test
    void getAuctionDetail_notFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminAuctionService.getAuctionDetail(id))
                .isInstanceOf(NotFoundException.class);
    }

    // -------------------------------------------------------
    // rejectAuction
    // -------------------------------------------------------

    @Test
    void rejectAuction_happyPath_transitionsToRejectedAndPublishesEvent() {
        AuctionItem item = buildItem(UUID.randomUUID(), AuctionStatus.SCHEDULED);
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(item.getId())).thenReturn(Optional.of(item));
        when(auctionItemRepository.save(any())).thenReturn(item);
        when(auctionMapper.toResponse(any(), any())).thenReturn(SellerAuctionResponse.builder().build());

        adminAuctionService.rejectAuction(ADMIN_ID, item.getId(), new AdminAuctionReasonRequest("Prohibited item"));

        assertThat(item.getStatus()).isEqualTo(AuctionStatus.REJECTED);
        assertThat(item.getRejectedBy()).isEqualTo(ADMIN_ID);
        assertThat(item.getRejectionReason()).isEqualTo("Prohibited item");

        ArgumentCaptor<AuctionRejectedEvent> captor = ArgumentCaptor.forClass(AuctionRejectedEvent.class);
        org.mockito.Mockito.verify(auctionKafkaProducer).publishAuctionRejected(captor.capture());
        assertThat(captor.getValue().getAuctionId()).isEqualTo(item.getId());
    }

    @Test
    void rejectAuction_notScheduled_throwsBadRequest() {
        AuctionItem item = buildItem(UUID.randomUUID(), AuctionStatus.ACTIVE);
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> adminAuctionService.rejectAuction(ADMIN_ID, item.getId(),
                new AdminAuctionReasonRequest("reason")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SCHEDULED");

        org.mockito.Mockito.verify(auctionKafkaProducer, never()).publishAuctionRejected(any());
    }

    @Test
    void rejectAuction_blankReason_throwsBadRequest() {
        AuctionItem item = buildItem(UUID.randomUUID(), AuctionStatus.SCHEDULED);
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> adminAuctionService.rejectAuction(ADMIN_ID, item.getId(),
                new AdminAuctionReasonRequest("  ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Rejection reason is required");
    }

    // -------------------------------------------------------
    // cancelAuction
    // -------------------------------------------------------

    @Test
    void cancelAuction_happyPath_transitionsToCancelledAndPublishesEvent() {
        AuctionItem item = buildItem(UUID.randomUUID(), AuctionStatus.ACTIVE);
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(item.getId())).thenReturn(Optional.of(item));
        when(auctionItemRepository.save(any())).thenReturn(item);
        when(auctionMapper.toResponse(any(), any())).thenReturn(SellerAuctionResponse.builder().build());

        adminAuctionService.cancelAuction(ADMIN_ID, item.getId(), new AdminAuctionReasonRequest("Fraud suspected"));

        assertThat(item.getStatus()).isEqualTo(AuctionStatus.CANCELLED);
        assertThat(item.getCancelledBy()).isEqualTo(ADMIN_ID);

        ArgumentCaptor<AuctionCancelledEvent> captor = ArgumentCaptor.forClass(AuctionCancelledEvent.class);
        org.mockito.Mockito.verify(auctionKafkaProducer).publishAuctionCancelled(captor.capture());
        assertThat(captor.getValue().getAuctionId()).isEqualTo(item.getId());
    }

    @Test
    void cancelAuction_notActive_throwsBadRequest() {
        AuctionItem item = buildItem(UUID.randomUUID(), AuctionStatus.SCHEDULED);
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> adminAuctionService.cancelAuction(ADMIN_ID, item.getId(),
                new AdminAuctionReasonRequest("reason")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void cancelAuction_blankReason_throwsBadRequest() {
        AuctionItem item = buildItem(UUID.randomUUID(), AuctionStatus.ACTIVE);
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> adminAuctionService.cancelAuction(ADMIN_ID, item.getId(),
                new AdminAuctionReasonRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cancellation reason is required");
    }

    // -------------------------------------------------------
    // forceCloseAuction
    // -------------------------------------------------------

    @Test
    void forceCloseAuction_happyPath_setsWinnerAndPublishesEvent() {
        AuctionItem item = buildItem(UUID.randomUUID(), AuctionStatus.ACTIVE);
        UUID winnerId = item.getCurrentWinnerId();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(item.getId())).thenReturn(Optional.of(item));
        when(auctionItemRepository.save(any())).thenReturn(item);
        when(auctionMapper.toResponse(any(), any())).thenReturn(SellerAuctionResponse.builder().build());

        adminAuctionService.forceCloseAuction(ADMIN_ID, item.getId(), new AdminAuctionReasonRequest(null));

        assertThat(item.getStatus()).isEqualTo(AuctionStatus.COMPLETED);
        assertThat(item.getWinnerId()).isEqualTo(winnerId);
        assertThat(item.getCompletedAt()).isNotNull();

        ArgumentCaptor<AuctionEndedEvent> captor = ArgumentCaptor.forClass(AuctionEndedEvent.class);
        org.mockito.Mockito.verify(auctionKafkaProducer).publishAuctionEnded(captor.capture());
        assertThat(captor.getValue().getClosureSource()).isEqualTo("ADMIN");
        assertThat(captor.getValue().getWinnerId()).isEqualTo(winnerId);
    }

    @Test
    void forceCloseAuction_notActive_throwsBadRequest() {
        AuctionItem item = buildItem(UUID.randomUUID(), AuctionStatus.SCHEDULED);
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> adminAuctionService.forceCloseAuction(ADMIN_ID, item.getId(), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ACTIVE");
    }

    @Test
    void forceCloseAuction_zeroBids_throwsBadRequest() {
        AuctionItem item = buildItem(UUID.randomUUID(), AuctionStatus.ACTIVE);
        item.setTotalBids(0);
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(item.getId())).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> adminAuctionService.forceCloseAuction(ADMIN_ID, item.getId(), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("no bids");

        org.mockito.Mockito.verify(auctionKafkaProducer, never()).publishAuctionEnded(any());
    }
}
