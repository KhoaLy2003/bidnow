package com.bidnow.wallet.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualRefundRequest {

    @NotBlank(message = "reason is required")
    @Size(max = 500, message = "reason must be at most 500 characters")
    private String reason;
}
