package com.bidnow.bidding.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** The caller's active bids, soonest-ending first, with per-tab counts. Not paginated. */
@Data
@Builder
public class MyActiveBidsResponse {
    private List<MyActiveBidResponse> items;
    private MyActiveBidCounts counts;
}
