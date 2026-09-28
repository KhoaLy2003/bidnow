package com.bidnow.wallet.controller;

import com.bidnow.common.annotation.AuthenticatedUserId;
import com.bidnow.common.dto.BaseResponse;
import com.bidnow.wallet.dto.request.ConfirmPaymentRequest;
import com.bidnow.wallet.dto.response.ConfirmPaymentResponse;
import com.bidnow.wallet.dto.response.PendingPaymentResponse;
import com.bidnow.wallet.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets/payments")
@RequiredArgsConstructor
@Tag(name = "Winner Payments", description = "Pending winner payments and payment confirmation")
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "List pending payments", description = "PENDING_PAYMENT holds for the current user, earliest deadline first.")
    @GetMapping("/pending")
    public ResponseEntity<BaseResponse<List<PendingPaymentResponse>>> getPendingPayments(@AuthenticatedUserId UUID userId) {
        return ResponseEntity.ok(BaseResponse.success(paymentService.getPendingPayments(userId)));
    }

    @Operation(summary = "Confirm payment", description = "Settles a won auction: debits the winner, credits the seller.")
    @PostMapping("/confirm")
    public ResponseEntity<BaseResponse<ConfirmPaymentResponse>> confirmPayment(@AuthenticatedUserId UUID userId,
                                                                               @Valid @RequestBody ConfirmPaymentRequest request) {
        return ResponseEntity.ok(BaseResponse.success(paymentService.confirmPayment(userId, request.getAuctionId())));
    }
}
