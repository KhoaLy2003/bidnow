package com.bidnow.auction.service.impl;

import com.bidnow.auction.domain.entity.AuctionCategory;
import com.bidnow.auction.domain.entity.AuctionImage;
import com.bidnow.auction.domain.entity.AuctionItem;
import com.bidnow.auction.domain.enums.AuctionSortBy;
import com.bidnow.auction.domain.enums.AuctionStatus;
import com.bidnow.auction.dto.request.PublicAuctionFilterRequest;
import com.bidnow.auction.dto.response.AuctionBrowseItem;
import com.bidnow.auction.dto.response.AuctionDetailResponse;
import com.bidnow.auction.feign.UserServiceClient;
import com.bidnow.auction.kafka.AuctionKafkaProducer;
import com.bidnow.auction.mapper.AuctionMapper;
import com.bidnow.auction.repository.AuctionCategoryRepository;
import com.bidnow.auction.repository.AuctionImageRepository;
import com.bidnow.auction.repository.AuctionItemRepository;
import com.bidnow.auction.repository.AuctionStatusHistoryRepository;
import com.bidnow.auction.service.AuctionClosureService;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.dto.UserSummaryResponse;
import com.bidnow.common.exception.BadRequestException;
import com.bidnow.common.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuctionServiceImplTest {

    @Mock
    private AuctionItemRepository auctionItemRepository;
    @Mock
    private AuctionCategoryRepository auctionCategoryRepository;
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
    @Mock
    private AuctionClosureService auctionClosureService;

    @InjectMocks
    private AuctionServiceImpl auctionService;

    // -------------------------------------------------------
    // Helpers
    // -------------------------------------------------------

    private AuctionItem buildItem(UUID id) {
        AuctionCategory category = AuctionCategory.builder()
                .id(UUID.randomUUID())
                .name("Electronics")
                .slug("electronics")
                .build();

        return AuctionItem.builder()
                .id(id)
                .sellerId(UUID.randomUUID())
                .title("Vintage Watch")
                .status(AuctionStatus.ACTIVE)
                .currentPrice(new BigDecimal("250.00"))
                .startingPrice(new BigDecimal("100.00"))
                .category(category)
                .build();
    }

    private PublicAuctionFilterRequest defaultFilter() {
        return PublicAuctionFilterRequest.builder().build();
    }

    @SuppressWarnings("unchecked")
    private void givenFindAllReturns(List<AuctionItem> items) {
        Page<AuctionItem> page = new PageImpl<>(items);
        when(auctionItemRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
    }

    // -------------------------------------------------------
    // browseAuctions
    // -------------------------------------------------------

    @Test
    void browseAuctions_happyPath_returnsMappedPage() {
        UUID id = UUID.randomUUID();
        AuctionItem item = buildItem(id);
        AuctionImage image = AuctionImage.builder()
                .imageUrl("https://img.example.com/watch.jpg")
                .auction(item)
                .build();
        AuctionBrowseItem browseItem = AuctionBrowseItem.builder()
                .id(id)
                .title("Vintage Watch")
                .build();

        givenFindAllReturns(List.of(item));
        when(auctionImageRepository.findByAuctionIdInOrderByDisplayOrderAsc(anyList()))
                .thenReturn(List.of(image));
        when(auctionMapper.toBrowseItem(eq(item), any())).thenReturn(browseItem);

        PageResponse<AuctionBrowseItem> result = auctionService.browseAuctions(defaultFilter());

        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getTitle()).isEqualTo("Vintage Watch");
        assertThat(result.getPagination().getTotal()).isEqualTo(1);
    }

    @Test
    void browseAuctions_minPriceGreaterThanMaxPrice_throwsBadRequest() {
        PublicAuctionFilterRequest filter = PublicAuctionFilterRequest.builder()
                .minPrice(new BigDecimal("500"))
                .maxPrice(new BigDecimal("100"))
                .build();

        assertThatThrownBy(() -> auctionService.browseAuctions(filter))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("minPrice");

        verify(auctionItemRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void browseAuctions_categorySlugNotFound_returnsEmptyPage() {
        when(auctionCategoryRepository.findBySlugAndIsActiveTrue("unknown-slug"))
                .thenReturn(Optional.empty());

        PublicAuctionFilterRequest filter = PublicAuctionFilterRequest.builder()
                .categorySlug("unknown-slug")
                .build();

        PageResponse<AuctionBrowseItem> result = auctionService.browseAuctions(filter);

        assertThat(result.getData()).isEmpty();
        assertThat(result.getPagination().getTotal()).isZero();
        verify(auctionItemRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void browseAuctions_categoryIdTakesPrecedenceOverSlug() {
        UUID categoryId = UUID.randomUUID();
        givenFindAllReturns(List.of());

        PublicAuctionFilterRequest filter = PublicAuctionFilterRequest.builder()
                .categoryId(categoryId)
                .categorySlug("some-slug")
                .build();

        auctionService.browseAuctions(filter);

        verify(auctionCategoryRepository, never()).findBySlugAndIsActiveTrue(any());
    }

    @Test
    void browseAuctions_emptyResultPage_returnsEmptyAndSkipsImageLoad() {
        givenFindAllReturns(List.of());

        PageResponse<AuctionBrowseItem> result = auctionService.browseAuctions(defaultFilter());

        assertThat(result.getData()).isEmpty();
        verify(auctionImageRepository, never()).findByAuctionIdInOrderByDisplayOrderAsc(anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void browseAuctions_sortByMostBids_usesTotalBidsDescending() {
        givenFindAllReturns(List.of());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        PublicAuctionFilterRequest filter = PublicAuctionFilterRequest.builder()
                .sortBy(AuctionSortBy.MOST_BIDS)
                .build();

        auctionService.browseAuctions(filter);

        verify(auctionItemRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("totalBids");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @SuppressWarnings("unchecked")
    void browseAuctions_sortByNewlyListed_usesCreatedAtDescending() {
        givenFindAllReturns(List.of());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        PublicAuctionFilterRequest filter = PublicAuctionFilterRequest.builder()
                .sortBy(AuctionSortBy.NEWLY_LISTED)
                .build();

        auctionService.browseAuctions(filter);

        verify(auctionItemRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @SuppressWarnings("unchecked")
    void browseAuctions_defaultSort_usesEndTimeAscending() {
        givenFindAllReturns(List.of());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        auctionService.browseAuctions(defaultFilter());

        verify(auctionItemRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("endTime");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void browseAuctions_imagesLoadedInSingleBatchQuery() {
        List<AuctionItem> items = List.of(buildItem(UUID.randomUUID()), buildItem(UUID.randomUUID()), buildItem(UUID.randomUUID()));
        givenFindAllReturns(items);
        when(auctionImageRepository.findByAuctionIdInOrderByDisplayOrderAsc(anyList()))
                .thenReturn(List.of());
        when(auctionMapper.toBrowseItem(any(), any()))
                .thenReturn(AuctionBrowseItem.builder().build());

        auctionService.browseAuctions(defaultFilter());

        verify(auctionImageRepository, times(1)).findByAuctionIdInOrderByDisplayOrderAsc(anyList());
    }

    // -------------------------------------------------------
    // getAuctionById
    // -------------------------------------------------------

    @Test
    void getAuctionById_userServiceReturnsProfile_sellerPopulated() {
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        AuctionItem item = buildItem(auctionId);
        item.setSellerId(sellerId);

        UserSummaryResponse seller = UserSummaryResponse.builder()
                .id(sellerId).name("Alice").avatarUrl("https://cdn.example.com/alice.jpg").build();
        AuctionDetailResponse expected = AuctionDetailResponse.builder()
                .id(auctionId).seller(seller).build();

        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)).thenReturn(Optional.of(item));
        when(auctionImageRepository.findByAuctionOrderByDisplayOrderAsc(item)).thenReturn(List.of());
        when(userServiceClient.getUserSummary(sellerId))
                .thenReturn(BaseResponse.<UserSummaryResponse>builder().data(seller).build());
        when(auctionMapper.toDetailResponse(eq(item), anyList(), eq(seller))).thenReturn(expected);

        AuctionDetailResponse result = auctionService.getAuctionById(auctionId);

        assertThat(result.getSeller()).isNotNull();
        assertThat(result.getSeller().getName()).isEqualTo("Alice");
    }

    @Test
    void getAuctionById_userServiceThrows_sellerIsNullAndAuctionStillReturned() {
        UUID auctionId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        AuctionItem item = buildItem(auctionId);
        item.setSellerId(sellerId);

        AuctionDetailResponse expected = AuctionDetailResponse.builder()
                .id(auctionId).seller(null).build();

        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)).thenReturn(Optional.of(item));
        when(auctionImageRepository.findByAuctionOrderByDisplayOrderAsc(item)).thenReturn(List.of());
        when(userServiceClient.getUserSummary(sellerId)).thenThrow(new RuntimeException("user-service down"));
        when(auctionMapper.toDetailResponse(eq(item), anyList(), eq(null))).thenReturn(expected);

        AuctionDetailResponse result = auctionService.getAuctionById(auctionId);

        assertThat(result).isNotNull();
        assertThat(result.getSeller()).isNull();
    }

    @Test
    void getAuctionById_unknownId_throwsNotFoundException() {
        UUID auctionId = UUID.randomUUID();
        when(auctionItemRepository.findByIdAndDeletedAtIsNull(auctionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionService.getAuctionById(auctionId))
                .isInstanceOf(NotFoundException.class);
    }

    // -------------------------------------------------------
    // getCategoryAuctionCounts
    // -------------------------------------------------------

//    @Test
//    void getCategoryAuctionCounts_returnsMappedList() {
//        UUID catId = UUID.randomUUID();
//        CategoryCountResponse entry = new CategoryCountResponse(catId, "Electronics", "electronics", 5L);
//        when(auctionItemRepository.countByStatusGroupByCategory(AuctionStatus.ACTIVE))
//                .thenReturn(List.of(entry));
//
//        List<CategoryCountResponse> result = auctionService.getCategoryAuctionCounts();
//
//        assertThat(result).hasSize(1);
//        assertThat(result.get(0).getCategoryName()).isEqualTo("Electronics");
//        assertThat(result.get(0).getCount()).isEqualTo(5L);
//    }
}
