package com.bidnow.auction.service.impl;

import com.bidnow.auction.domain.entity.AuctionImage;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.request.AdminAuctionFilterRequest;
import com.bidnow.auction.dto.request.AdminAuctionReasonRequest;
import com.bidnow.auction.dto.response.AdminAuctionDetailResponse;
import com.bidnow.auction.dto.response.AdminAuctionSummaryResponse;
import com.bidnow.auction.dto.response.AuctionStatusHistoryResponse;
import com.bidnow.auction.dto.response.SellerAuctionResponse;
import com.bidnow.auction.feign.UserServiceClient;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.mapper.AuctionMapper;
import com.bidnow.auction.repository.AuctionImageRepository;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.auction.service.AdminAuctionService;
import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionEndedEvent;
import com.bidnow.common.dto.event.AuctionRejectedEvent;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.specification.SearchOperator;
import com.bidnow.common.specification.SpecificationBuilder;
import com.bidnow.common.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAuctionServiceImpl implements AdminAuctionService {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    private final AuctionMapper auctionMapper;
    private final AuctionKafkaProducer auctionKafkaProducer;
    private final UserServiceClient userServiceClient;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminAuctionSummaryResponse> listAuctions(AdminAuctionFilterRequest filter) {
        List<AuctionStatus> statusFilter = parseStatuses(filter.getStatus());

        Specification<AuctionItem> spec = SpecificationBuilder.<AuctionItem>forEntity()
                .withIsNull("deletedAt")
                .withInIfPresent("status", statusFilter)
                .withIfPresent("category.id", SearchOperator.EQUAL, filter.getCategoryId())
                .withIfPresent("sellerId", SearchOperator.EQUAL, filter.getSellerId())
                .withLikeIfPresent("title", filter.getQ())
                .build();

        Pageable pageable = PaginationUtils.getPageable(filter.getPage(), filter.getSize(), filter.getSortBy(), filter.getSortDir());
        Page<AuctionItem> page = auctionItemRepository.findAll(spec, pageable);

        List<AdminAuctionSummaryResponse> summaries = page.getContent().stream()
                .map(item -> auctionMapper.toAdminSummaryResponse(item, fetchSellerName(item.getSellerId())))
                .toList();

        return PaginationUtils.toPageResponse(page, summaries);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminAuctionDetailResponse getAuctionDetail(UUID id) {
        AuctionItem auction = findById(id);
        List<AuctionImage> images = auctionImageRepository.findByAuctionOrderByDisplayOrderAsc(auction);
        List<AuctionStatusHistoryResponse> history = auctionStatusHistoryRepository
                .findByAuction_IdOrderByCreatedAtDesc(id)
                .stream()
                .map(auctionMapper::toStatusHistoryResponse)
                .toList();
        String sellerName = fetchSellerName(auction.getSellerId());
        return auctionMapper.toAdminDetailResponse(auction, images, sellerName, history);
    }

    @Override
    @Transactional
    public SellerAuctionResponse rejectAuction(UUID adminId, UUID id, AdminAuctionReasonRequest request) {
        AuctionItem auction = findById(id);

        if (auction.getStatus() != AuctionStatus.SCHEDULED) {
            throw new BadRequestException(
                    "Auction must be in SCHEDULED status to be rejected", ErrorCodes.INVALID_INPUT);
        }

        String reason = requireReason(request, "Rejection reason is required");

        OffsetDateTime now = OffsetDateTime.now();
        AuctionStatus oldStatus = auction.getStatus();

        auction.setStatus(AuctionStatus.REJECTED);
        auction.setRejectionReason(reason);
        auction.setRejectedBy(adminId);
        auction.setRejectedAt(now);
        auction = auctionItemRepository.save(auction);

        recordStatusHistory(auction, oldStatus, AuctionStatus.REJECTED, adminId, reason);

        auctionKafkaProducer.publishAuctionRejected(AuctionRejectedEvent.builder()
                .auctionId(auction.getId())
                .sellerId(auction.getSellerId())
                .title(auction.getTitle())
                .rejectedBy(adminId)
                .reason(reason)
                .rejectedAt(now.toInstant())
                .build());

        log.info("Admin {} rejected auction {} (was {})", adminId, id, oldStatus);
        return toResponse(auction);
    }

    @Override
    @Transactional
    public SellerAuctionResponse cancelAuction(UUID adminId, UUID id, AdminAuctionReasonRequest request) {
        AuctionItem auction = findById(id);

        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new BadRequestException(
                    "Auction must be ACTIVE to be cancelled", ErrorCodes.INVALID_INPUT);
        }

        String reason = requireReason(request, "Cancellation reason is required");

        OffsetDateTime now = OffsetDateTime.now();
        AuctionStatus oldStatus = auction.getStatus();

        auction.setStatus(AuctionStatus.CANCELLED);
        auction.setCancellationReason(reason);
        auction.setCancelledBy(adminId);
        auction.setCancelledAt(now);
        auction = auctionItemRepository.save(auction);

        recordStatusHistory(auction, oldStatus, AuctionStatus.CANCELLED, adminId, reason);

        auctionKafkaProducer.publishAuctionCancelled(AuctionCancelledEvent.builder()
                .auctionId(auction.getId())
                .sellerId(auction.getSellerId())
                .auctionTitle(auction.getTitle())
                .previousStatus(oldStatus.name())
                .reason(reason)
                .cancelledAt(now.toInstant())
                .build());

        log.info("Admin {} cancelled auction {} (was {})", adminId, id, oldStatus);
        return toResponse(auction);
    }

    @Override
    @Transactional
    public SellerAuctionResponse forceCloseAuction(UUID adminId, UUID id, AdminAuctionReasonRequest request) {
        AuctionItem auction = findById(id);

        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            throw new BadRequestException(
                    "Auction must be ACTIVE to be force-closed", ErrorCodes.INVALID_INPUT);
        }

        if (auction.getTotalBids() == null || auction.getTotalBids() == 0) {
            throw new BadRequestException(
                    "Cannot force-close an auction with no bids", ErrorCodes.INVALID_INPUT);
        }

        String reason = request != null ? request.getReason() : null;
        OffsetDateTime now = OffsetDateTime.now();
        AuctionStatus oldStatus = auction.getStatus();

        auction.setStatus(AuctionStatus.COMPLETED);
        auction.setWinnerId(auction.getCurrentWinnerId());
        auction.setCompletedAt(now);
        auction = auctionItemRepository.save(auction);

        recordStatusHistory(auction, oldStatus, AuctionStatus.COMPLETED, adminId, reason);

        auctionKafkaProducer.publishAuctionEnded(AuctionEndedEvent.builder()
                .auctionId(auction.getId())
                .auctionTitle(auction.getTitle())
                .sellerId(auction.getSellerId())
                .winnerId(auction.getWinnerId())
                .winningBidAmount(auction.getCurrentPrice())
                .totalBids(auction.getTotalBids())
                .endedAt(now.toInstant())
                .closureSource("ADMIN")
                .build());

        log.info("Admin {} force-closed auction {} with winner {}", adminId, id, auction.getWinnerId());
        return toResponse(auction);
    }

    private AuctionItem findById(UUID id) {
        return auctionItemRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Auction not found", ErrorCodes.NOT_FOUND));
    }

    private String requireReason(AdminAuctionReasonRequest request, String errorMessage) {
        String reason = request != null ? request.getReason() : null;
        if (!StringUtils.hasText(reason)) {
            throw new BadRequestException(errorMessage, ErrorCodes.INVALID_INPUT);
        }
        return reason;
    }

    private List<AuctionStatus> parseStatuses(String statusParam) {
        if (!StringUtils.hasText(statusParam)) {
            return List.of();
        }
        return Arrays.stream(statusParam.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(AuctionStatus::valueOf)
                .toList();
    }

    private String fetchSellerName(UUID sellerId) {
        try {
            UserSummaryResponse summary = userServiceClient.getUserSummary(sellerId).getData();
            return summary != null ? summary.getName() : null;
        } catch (Exception e) {
            log.warn("Could not fetch seller summary for sellerId={}: {}", sellerId, e.getMessage());
            return null;
        }
    }

    private SellerAuctionResponse toResponse(AuctionItem auction) {
        List<AuctionImage> images = auctionImageRepository.findByAuctionOrderByDisplayOrderAsc(auction);
        return auctionMapper.toResponse(auction, images);
    }

    private void recordStatusHistory(AuctionItem auction, AuctionStatus fromStatus, AuctionStatus toStatus,
                                     UUID triggeredBy, String reason) {
        AuctionStatusHistory history = AuctionStatusHistory.builder()
                .auction(auction)
                .fromStatus(fromStatus != null ? fromStatus.name() : null)
                .toStatus(toStatus.name())
                .triggeredBy(triggeredBy)
                .reason(reason)
                .build();
        auctionStatusHistoryRepository.save(history);
    }
}
