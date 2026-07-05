package com.bidnow.auction.job;

import com.bidnow.auction.service.AuctionClosureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuctionClosureJobTest {

    @Mock
    private AuctionClosureService closureService;

    @InjectMocks
    private AuctionClosureJob auctionClosureJob;

    @Test
    void closeAuction_delegatesToService() {
        UUID auctionId = UUID.randomUUID();
        auctionClosureJob.closeAuction(auctionId);
        verify(closureService).close(auctionId);
    }
}
