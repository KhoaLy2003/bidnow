package com.bidnow.auction.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Reason body for admin moderation actions (reject/cancel/force-close)")
public class AdminAuctionReasonRequest {

    @Size(max = 500, message = "Reason must not exceed 500 characters")
    @Schema(description = "Reason for the action. Required for reject/cancel, optional for force-close")
    private String reason;
}
