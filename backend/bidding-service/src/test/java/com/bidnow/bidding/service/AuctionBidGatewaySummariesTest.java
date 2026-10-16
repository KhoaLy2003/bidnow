package com.bidnow.bidding.service;

import com.bidnow.bidding.dto.AuctionBidSummariesCommand;
import com.bidnow.bidding.dto.AuctionBidSummary;
import com.bidnow.bidding.exception.ServiceUnavailableException;
import com.bidnow.bidding.feign.AuctionServiceClient;
import com.bidnow.common.dto.BaseResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The batch "active summaries" call of {@link AuctionBidGateway}: chunking and error mapping. */
@ExtendWith(MockitoExtension.class)
class AuctionBidGatewaySummariesTest {

    @Mock
    private AuctionServiceClient auctionServiceClient;

    private AuctionBidGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new AuctionBidGateway(auctionServiceClient, new ObjectMapper());
    }

    private static List<UUID> ids(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    private static AuctionBidSummary summary(UUID auctionId) {
        return AuctionBidSummary.builder().auctionId(auctionId).title("t").build();
    }

    @Test
    void noIds_makesNoCall() {
        assertThat(gateway.activeSummaries(List.of())).isEmpty();

        verifyNoInteractions(auctionServiceClient);
    }

    @Test
    void splitsIdsIntoChunksOf100AndConcatenatesTheResults() {
        List<UUID> ids = ids(250);
        when(auctionServiceClient.getBidSummaries(any())).thenAnswer(invocation -> {
            AuctionBidSummariesCommand command = invocation.getArgument(0);
            return BaseResponse.success(command.auctionIds().stream().map(AuctionBidGatewaySummariesTest::summary).toList());
        });

        List<AuctionBidSummary> result = gateway.activeSummaries(ids);

        ArgumentCaptor<AuctionBidSummariesCommand> commands = ArgumentCaptor.forClass(AuctionBidSummariesCommand.class);
        verify(auctionServiceClient, times(3)).getBidSummaries(commands.capture());
        assertThat(commands.getAllValues()).extracting(c -> c.auctionIds().size()).containsExactly(100, 100, 50);
        assertThat(result).extracting(AuctionBidSummary::getAuctionId).containsExactlyElementsOf(ids);
    }

    @Test
    void downstreamFailure_isReportedAsServiceUnavailable() {
        Request request = Request.create(Request.HttpMethod.POST, "/api/v1/internal/auctions/bid-summaries",
                Map.of(), null, StandardCharsets.UTF_8, null);
        when(auctionServiceClient.getBidSummaries(any()))
                .thenThrow(new FeignException.InternalServerError("boom", request, null, Map.of()));

        assertThatThrownBy(() -> gateway.activeSummaries(ids(3))).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void emptyResponseBody_isReportedAsServiceUnavailable() {
        when(auctionServiceClient.getBidSummaries(any())).thenReturn(null);

        assertThatThrownBy(() -> gateway.activeSummaries(ids(3))).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void responseWithoutData_isReportedAsServiceUnavailable() {
        when(auctionServiceClient.getBidSummaries(any())).thenReturn(BaseResponse.success(null));

        assertThatThrownBy(() -> gateway.activeSummaries(ids(3))).isInstanceOf(ServiceUnavailableException.class);
    }
}
