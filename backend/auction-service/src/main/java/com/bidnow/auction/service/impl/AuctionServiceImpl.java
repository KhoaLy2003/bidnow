package com.bidnow.auction.service.impl;

import com.bidnow.auction.domain.entity.AuctionCategory;
import com.bidnow.auction.domain.entity.AuctionImage;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.entity.AuctionStatusHistory;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.request.CreateAuctionRequest;
import com.bidnow.auction.dto.request.UpdateAuctionRequest;
import com.bidnow.auction.dto.response.AuctionCategoryResponse;
import com.bidnow.auction.dto.response.AuctionResponse;
import com.bidnow.auction.dto.response.AuctionSummaryResponse;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.mapper.AuctionMapper;
import com.bidnow.auction.repository.AuctionCategoryRepository;
import com.bidnow.auction.repository.AuctionImageRepository;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.auction.service.AuctionService;
import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.event.AuctionCreatedEvent;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.ForbiddenException;
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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionServiceImpl implements AuctionService {

    private final AuctionItemRepository auctionItemRepository;
    private final AuctionCategoryRepository auctionCategoryRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final AuctionStatusHistoryRepository auctionStatusHistoryRepository;
    private final AuctionMapper auctionMapper;
    private final AuctionKafkaProducer auctionKafkaProducer;

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
                    .endTime(auction.getEndTime().toLocalDateTime())
                    .build());
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

        List<AuctionSummaryResponse> summaries = page.getContent().stream()
                .map(item -> {
                    List<AuctionImage> images = auctionImageRepository.findByAuctionOrderByDisplayOrderAsc(item);
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

        auctionMapper.updateFromRequest(request, auction);

        if (request.getImageUrls() != null) {
            if (request.getImageUrls().size() < 1 || request.getImageUrls().size() > 10) {
                throw new BadRequestException("Must provide between 1 and 10 images", ErrorCodes.INVALID_INPUT);
            }
            auctionImageRepository.deleteByAuction(auction);
            List<AuctionImage> newImages = buildImages(auction, request.getImageUrls());
            auctionImageRepository.saveAll(newImages);
        }

        auction = auctionItemRepository.save(auction);

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

        if (auction.getStatus() != AuctionStatus.DRAFT &&
                !auction.getStartTime().isAfter(OffsetDateTime.now())) {
            throw new BadRequestException("Auction cannot be deleted after it has started", ErrorCodes.INVALID_INPUT);
        }

        auction.setDeletedAt(OffsetDateTime.now());
        auctionItemRepository.save(auction);

        log.info("Soft-deleted auction {} by seller {}", auctionId, sellerId);
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
