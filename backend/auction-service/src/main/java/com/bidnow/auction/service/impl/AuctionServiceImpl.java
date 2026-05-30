package com.bidnow.auction.service.impl;

import com.bidnow.auction.config.CacheConfig;
import com.bidnow.auction.domain.entity.AuctionCategory;
import com.bidnow.auction.domain.entity.AuctionImage;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionSortBy;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.request.CancelAuctionRequest;
import com.bidnow.auction.dto.request.CreateAuctionRequest;
import com.bidnow.auction.dto.request.PublicAuctionFilterRequest;
import com.bidnow.auction.dto.request.UpdateAuctionRequest;
import com.bidnow.auction.dto.response.AuctionResponse;
import com.bidnow.auction.dto.response.AuctionSummaryResponse;
import com.bidnow.auction.dto.response.CategoryCountResponse;
import com.bidnow.auction.job.AuctionActivationJob;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.mapper.AuctionMapper;
import com.bidnow.auction.repository.AuctionCategoryRepository;
import com.bidnow.auction.repository.AuctionImageRepository;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.auction.service.AuctionService;
import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.event.AuctionCancelledEvent;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.ForbiddenException;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.specification.SearchOperator;
import com.bidnow.common.specification.SpecificationBuilder;
import com.bidnow.common.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jobrunr.scheduling.BackgroundJob;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionServiceImpl implements AuctionService {

    private static final Set<AuctionStatus> TERMINAL_STATUSES =
            Set.of(AuctionStatus.CANCELLED, AuctionStatus.COMPLETED, AuctionStatus.FAILED);

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionCategoryRepository auctionCategoryRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    private final AuctionMapper auctionMapper;
    private final AuctionKafkaProducer auctionKafkaProducer;

    @Override
    @Transactional(readOnly = true)
    public AuctionResponse getAuctionById(UUID id) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Auction not found", ErrorCodes.NOT_FOUND));
        List<AuctionImage> images = auctionImageRepository.findByAuctionOrderByDisplayOrderAsc(auction);
        return auctionMapper.toResponse(auction, images);
    }

    @Override
    @Transactional
    public AuctionResponse publishAuction(UUID sellerId, UUID id) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Auction not found", ErrorCodes.NOT_FOUND));

        if (!auction.getSellerId().equals(sellerId)) {
            throw new ForbiddenException("You do not own this auction", ErrorCodes.ACCESS_DENIED);
        }

        if (auction.getStatus() != AuctionStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT auctions can be published", ErrorCodes.INVALID_INPUT);
        }

        OffsetDateTime now = OffsetDateTime.now();

        if (!auction.getEndTime().isAfter(now)) {
            throw new BadRequestException("Auction end time must be in the future", ErrorCodes.INVALID_INPUT);
        }

        if (!auction.getEndTime().isAfter(auction.getStartTime())) {
            throw new BadRequestException("End time must be after start time", ErrorCodes.INVALID_INPUT);
        }

        AuctionStatus oldStatus = auction.getStatus();
        AuctionStatus newStatus = auction.getStartTime().isAfter(now)
                ? AuctionStatus.SCHEDULED
                : AuctionStatus.ACTIVE;

        auction.setStatus(newStatus);
        auction = auctionItemRepository.save(auction);

        recordStatusHistory(auction, oldStatus, newStatus, sellerId, "Seller published auction");

        if (newStatus == AuctionStatus.ACTIVE) {
            auctionKafkaProducer.publishAuctionCreated(AuctionCreatedEvent.builder()
                    .auctionId(auction.getId())
                    .sellerId(sellerId)
                    .title(auction.getTitle())
                    .startingPrice(auction.getStartingPrice())
                    .endTime(auction.getEndTime().toInstant())
                    .build());
        } else if (newStatus == AuctionStatus.SCHEDULED) {
            scheduleActivationJob(auction.getId(), auction.getStartTime().toInstant());
        }

        log.info("Published auction {} to status {} by seller {}", id, newStatus, sellerId);
        List<AuctionImage> images = auctionImageRepository.findByAuctionOrderByDisplayOrderAsc(auction);
        return auctionMapper.toResponse(auction, images);
    }

    @Override
    @Transactional
    public void cancelAuction(UUID sellerId, UUID id, CancelAuctionRequest request) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Auction not found", ErrorCodes.NOT_FOUND));

        if (!auction.getSellerId().equals(sellerId)) {
            throw new ForbiddenException("You do not own this auction", ErrorCodes.ACCESS_DENIED);
        }

        Set<AuctionStatus> cancellable = Set.of(
                AuctionStatus.DRAFT, AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE);
        if (!cancellable.contains(auction.getStatus())) {
            throw new BadRequestException(
                    "Auction cannot be cancelled in status: " + auction.getStatus(), ErrorCodes.INVALID_INPUT);
        }

        AuctionStatus oldStatus = auction.getStatus();
        OffsetDateTime now = OffsetDateTime.now();
        String reason = (request != null && request.getReason() != null)
                ? request.getReason() : "Seller cancelled auction";

        auction.setStatus(AuctionStatus.CANCELLED);
        auction.setCancelledAt(now);
        auction.setCancelledBy(sellerId);
        auction.setCancellationReason(reason);
        auctionItemRepository.save(auction);

        recordStatusHistory(auction, oldStatus, AuctionStatus.CANCELLED, sellerId, reason);

        if (oldStatus == AuctionStatus.ACTIVE || oldStatus == AuctionStatus.SCHEDULED) {
            auctionKafkaProducer.publishAuctionCancelled(AuctionCancelledEvent.builder()
                    .auctionId(auction.getId())
                    .sellerId(sellerId)
                    .auctionTitle(auction.getTitle())
                    .previousStatus(oldStatus.name())
                    .reason(reason)
                    .cancelledAt(now.toInstant())
                    .build());
        }

        log.info("Cancelled auction {} (was {}) by seller {}", id, oldStatus, sellerId);
    }

    @Override
    @Transactional
    public AuctionResponse createAuction(UUID sellerId, CreateAuctionRequest request) {
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BadRequestException("End time must be after start time", ErrorCodes.INVALID_INPUT);
        }

        if (request.getBuyNowPrice() != null &&
                request.getBuyNowPrice().compareTo(request.getStartingPrice()) <= 0) {
            throw new BadRequestException("Buy now price must be greater than starting price", ErrorCodes.INVALID_INPUT);
        }

        AuctionStatus status = resolveStatus(request.getStatus(), request.getStartTime());

        AuctionCategory category = auctionCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new NotFoundException("Category not found", ErrorCodes.NOT_FOUND));

        AuctionItem auction = AuctionItem.builder()
                .sellerId(sellerId)
                .title(request.getTitle())
                .description(request.getDescription())
                .category(category)
                .startingPrice(request.getStartingPrice())
                .bidIncrement(request.getBidIncrement())
                .buyNowPrice(request.getBuyNowPrice())
                .depositAmount(request.getDepositAmount())
                .currentPrice(request.getStartingPrice())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .originalEndTime(request.getEndTime())
                .status(status)
                .build();

        auction = auctionItemRepository.save(auction);

        List<AuctionImage> images = buildImages(auction, request.getImageUrls());
        auctionImageRepository.saveAll(images);

        recordStatusHistory(auction, null, status, sellerId, "Auction created");

        if (status == AuctionStatus.ACTIVE) {
            auctionKafkaProducer.publishAuctionCreated(AuctionCreatedEvent.builder()
                    .auctionId(auction.getId())
                    .sellerId(sellerId)
                    .title(auction.getTitle())
                    .startingPrice(auction.getStartingPrice())
                    .endTime(auction.getEndTime().toInstant())
                    .build());
        } else if (status == AuctionStatus.SCHEDULED) {
            scheduleActivationJob(auction.getId(), auction.getStartTime().toInstant());
        }

        log.info("Created auction {} with status {} for seller {}", auction.getId(), status, sellerId);
        return auctionMapper.toResponse(auction, images);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuctionSummaryResponse> getMyAuctions(UUID sellerId, String type, UUID categoryId, Pageable pageable) {
        List<AuctionStatus> statusFilter = "history".equalsIgnoreCase(type)
                ? List.of(AuctionStatus.COMPLETED, AuctionStatus.FAILED, AuctionStatus.CANCELLED)
                : List.of(AuctionStatus.DRAFT, AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE);

        Specification<AuctionItem> spec = SpecificationBuilder.<AuctionItem>forEntity()
                .with("sellerId", SearchOperator.EQUAL, sellerId)
                .withIsNull("deletedAt")
                .withIn("status", statusFilter)
                .withIfPresent("category.id", SearchOperator.EQUAL, categoryId)
                .build();

        Page<AuctionItem> page = auctionItemRepository.findAll(spec, pageable);

        List<AuctionItem> auctions = page.getContent();
        if (auctions.isEmpty()) {
            return PaginationUtils.toPageResponse(page, List.of());
        }
        List<UUID> auctionIds = auctions.stream().map(AuctionItem::getId).toList();
        Map<UUID, List<AuctionImage>> imagesByAuction = auctionImageRepository
                .findByAuctionIdInOrderByDisplayOrderAsc(auctionIds)
                .stream()
                .collect(Collectors.groupingBy(img -> img.getAuction().getId()));
        List<AuctionSummaryResponse> summaries = auctions.stream()
                .map(item -> {
                    List<AuctionImage> images = imagesByAuction.getOrDefault(item.getId(), List.of());
                    AuctionImage primary = images.stream()
                            .filter(AuctionImage::getIsPrimary)
                            .findFirst()
                            .orElse(images.isEmpty() ? null : images.get(0));
                    return auctionMapper.toSummaryResponse(item, primary);
                })
                .toList();

        return PaginationUtils.toPageResponse(page, summaries);
    }

    @Override
    @Transactional
    public AuctionResponse updateAuction(UUID sellerId, UUID auctionId, UpdateAuctionRequest request) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)
                .orElseThrow(() -> new NotFoundException("Auction not found", ErrorCodes.NOT_FOUND));

        if (!auction.getSellerId().equals(sellerId)) {
            throw new ForbiddenException("You do not own this auction", ErrorCodes.ACCESS_DENIED);
        }

        if (TERMINAL_STATUSES.contains(auction.getStatus())) {
            throw new BadRequestException(
                    "Cannot modify a " + auction.getStatus() + " auction", ErrorCodes.INVALID_INPUT);
        }

        if (auction.getStatus() != AuctionStatus.DRAFT &&
                !auction.getStartTime().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("Auction cannot be modified after it has started", ErrorCodes.INVALID_INPUT);
        }

        if (request.getCategoryId() != null) {
            AuctionCategory category = auctionCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new NotFoundException("Category not found", ErrorCodes.NOT_FOUND));
            auction.setCategory(category);
        }

        OffsetDateTime effectiveStartTime = request.getStartTime() != null ? request.getStartTime() : auction.getStartTime();
        OffsetDateTime effectiveEndTime = request.getEndTime() != null ? request.getEndTime() : auction.getEndTime();
        if (!effectiveEndTime.isAfter(effectiveStartTime)) {
            throw new BadRequestException("End time must be after start time", ErrorCodes.INVALID_INPUT);
        }

        if (request.getBuyNowPrice() != null || request.getStartingPrice() != null) {
            BigDecimal effectiveStartingPrice = request.getStartingPrice() != null ? request.getStartingPrice() : auction.getStartingPrice();
            BigDecimal effectiveBuyNowPrice = request.getBuyNowPrice() != null ? request.getBuyNowPrice() : auction.getBuyNowPrice();
            if (effectiveBuyNowPrice != null && effectiveBuyNowPrice.compareTo(effectiveStartingPrice) <= 0) {
                throw new BadRequestException("Buy now price must be greater than starting price", ErrorCodes.INVALID_INPUT);
            }
        }

        // Validate image count BEFORE mutating the entity via mapper
        if (request.getImageUrls() != null &&
                (request.getImageUrls().size() < 1 || request.getImageUrls().size() > 10)) {
            throw new BadRequestException("Must provide between 1 and 10 images", ErrorCodes.INVALID_INPUT);
        }

        boolean wasScheduled = auction.getStatus() == AuctionStatus.SCHEDULED;
        OffsetDateTime oldStartTime = auction.getStartTime();

        auctionMapper.updateFromRequest(request, auction);

        if (request.getImageUrls() != null) {
            auctionImageRepository.deleteByAuction(auction);
            List<AuctionImage> newImages = buildImages(auction, request.getImageUrls());
            auctionImageRepository.saveAll(newImages);
        }

        auction = auctionItemRepository.save(auction);

        if (wasScheduled
                && request.getStartTime() != null
                && !request.getStartTime().toInstant().equals(oldStartTime.toInstant())) {
            UUID jobId = activationJobId(auctionId);
            Instant newStartInstant = auction.getStartTime().toInstant();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        BackgroundJob.delete(jobId);
                    } catch (Exception e) {
                        log.warn("Could not delete old activation job {} (may have already executed): {}",
                                jobId, e.getMessage());
                    }
                    BackgroundJob.<AuctionActivationJob>schedule(
                            jobId, newStartInstant, job -> job.activateAuction(auctionId));
                    log.info("Rescheduled activation job {} for auction {} at {}",
                            jobId, auctionId, newStartInstant);
                }
            });
        }

        log.info("Updated auction {} by seller {}", auctionId, sellerId);
        List<AuctionImage> images = auctionImageRepository.findByAuctionOrderByDisplayOrderAsc(auction);
        return auctionMapper.toResponse(auction, images);
    }

    @Override
    @Transactional
    public void deleteAuction(UUID sellerId, UUID auctionId) {
        AuctionItem auction = auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)
                .orElseThrow(() -> new NotFoundException("Auction not found", ErrorCodes.NOT_FOUND));

        if (!auction.getSellerId().equals(sellerId)) {
            throw new ForbiddenException("You do not own this auction", ErrorCodes.ACCESS_DENIED);
        }

        if (TERMINAL_STATUSES.contains(auction.getStatus())) {
            throw new BadRequestException(
                    "Cannot delete a " + auction.getStatus() + " auction", ErrorCodes.INVALID_INPUT);
        }

        if (auction.getStatus() != AuctionStatus.DRAFT &&
                !auction.getStartTime().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("Auction cannot be deleted after it has started", ErrorCodes.INVALID_INPUT);
        }

        auction.setDeletedAt(OffsetDateTime.now());
        auctionItemRepository.save(auction);

        log.info("Soft-deleted auction {} by seller {}", auctionId, sellerId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuctionSummaryResponse> browseAuctions(PublicAuctionFilterRequest filter) {
        if (filter.getMinPrice() != null && filter.getMaxPrice() != null
                && filter.getMinPrice().compareTo(filter.getMaxPrice()) > 0) {
            throw new BadRequestException("minPrice must not be greater than maxPrice", ErrorCodes.INVALID_INPUT);
        }

        // categoryId takes precedence; fall back to slug lookup
        UUID resolvedCategoryId = filter.getCategoryId();
        if (resolvedCategoryId == null && StringUtils.hasText(filter.getCategorySlug())) {
            Optional<AuctionCategory> cat =
                    auctionCategoryRepository.findBySlugAndIsActiveTrue(filter.getCategorySlug());
            if (cat.isEmpty()) {
                PageRequest emptyPageable = PageRequest.of(filter.getPage(), filter.getSize());
                return PaginationUtils.toPageResponse(new PageImpl<>(List.of(), emptyPageable, 0), List.of());
            }
            resolvedCategoryId = cat.get().getId();
        }

        OffsetDateTime endingSoonFrom = null;
        OffsetDateTime endingSoonTo = null;
        if (Boolean.TRUE.equals(filter.getEndingSoon())) {
            endingSoonFrom = OffsetDateTime.now();
            endingSoonTo = endingSoonFrom.plusHours(24);
        }

        Specification<AuctionItem> spec = SpecificationBuilder.<AuctionItem>forEntity()
                .with("status", SearchOperator.EQUAL, AuctionStatus.ACTIVE)
                .withIsNull("deletedAt")
                .withIfPresent("category.id", SearchOperator.EQUAL, resolvedCategoryId)
                .withIfPresent("currentPrice", SearchOperator.GREATER_THAN_OR_EQUAL, filter.getMinPrice())
                .withIfPresent("currentPrice", SearchOperator.LESS_THAN_OR_EQUAL, filter.getMaxPrice())
                .withLikeIfPresent("title", filter.getKeyword())
                .withBetweenIfPresent("endTime", endingSoonFrom, endingSoonTo)
                .build();

        if (Boolean.TRUE.equals(filter.getBuyNowAvailable())) {
            spec = spec.and((root, query, cb) -> cb.isNotNull(root.get("buyNowPrice")));
        }

        AuctionSortBy sortBy = filter.getSortBy() != null ? filter.getSortBy() : AuctionSortBy.END_TIME_ASC;
        Sort sort = switch (sortBy) {
            case NEWLY_LISTED   -> Sort.by("createdAt").descending();
            case PRICE_LOW_HIGH -> Sort.by("currentPrice").ascending();
            case PRICE_HIGH_LOW -> Sort.by("currentPrice").descending();
            case MOST_BIDS      -> Sort.by("totalBids").descending();
            default             -> Sort.by("endTime").ascending();
        };

        PageRequest pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);
        Page<AuctionItem> page = auctionItemRepository.findAll(spec, pageable);

        if (page.isEmpty()) {
            return PaginationUtils.toPageResponse(page, List.of());
        }

        List<UUID> auctionIds = page.getContent().stream().map(AuctionItem::getId).toList();
        Map<UUID, List<AuctionImage>> imagesByAuction = auctionImageRepository
                .findByAuctionIdInOrderByDisplayOrderAsc(auctionIds)
                .stream()
                .collect(Collectors.groupingBy(img -> img.getAuction().getId()));

        List<AuctionSummaryResponse> summaries = page.getContent().stream()
                .map(auction -> {
                    List<AuctionImage> images = imagesByAuction.getOrDefault(auction.getId(), List.of());
                    AuctionImage primary = images.isEmpty() ? null : images.get(0);
                    return auctionMapper.toSummaryResponse(auction, primary);
                })
                .toList();

        return PaginationUtils.toPageResponse(page, summaries);
    }

    @Override
    @Cacheable(value = CacheConfig.CACHE_CATEGORY_COUNTS, key = "'active'")
    public List<CategoryCountResponse> getCategoryAuctionCounts() {
        return auctionItemRepository.countByStatusGroupByCategory(AuctionStatus.ACTIVE);
    }

    private void scheduleActivationJob(UUID auctionId, Instant activateAt) {
        UUID jobId = activationJobId(auctionId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                BackgroundJob.<AuctionActivationJob>schedule(jobId, activateAt, job -> job.activateAuction(auctionId));
                log.info("Scheduled activation job {} for auction {} at {}", jobId, auctionId, activateAt);
            }
        });
    }

    private static UUID activationJobId(UUID auctionId) {
        return UUID.nameUUIDFromBytes(
                ("auction-activation:" + auctionId).getBytes(StandardCharsets.UTF_8));
    }

    private AuctionStatus resolveStatus(AuctionStatus requested, OffsetDateTime startTime) {
        // Seller can explicitly save as DRAFT regardless of start time
        if (requested == AuctionStatus.DRAFT) {
            return AuctionStatus.DRAFT;
        }
        // startTime in the future → SCHEDULED; now or past → ACTIVE immediately
        return startTime.isAfter(OffsetDateTime.now())
                ? AuctionStatus.SCHEDULED
                : AuctionStatus.ACTIVE;
    }

    private List<AuctionImage> buildImages(AuctionItem auction, List<String> imageUrls) {
        List<AuctionImage> images = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            images.add(AuctionImage.builder()
                    .auction(auction)
                    .imageUrl(imageUrls.get(i))
                    .displayOrder(i)
                    .isPrimary(i == 0)
                    .build());
        }
        return images;
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
