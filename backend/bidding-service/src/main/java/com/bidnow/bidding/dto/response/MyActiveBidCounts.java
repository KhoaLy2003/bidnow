package com.bidnow.bidding.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MyActiveBidCounts {
    private int all;
    private int winning;
    private int outbid;
}
