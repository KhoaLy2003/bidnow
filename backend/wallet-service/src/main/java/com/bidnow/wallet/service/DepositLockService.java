package com.bidnow.wallet.service;

import com.bidnow.wallet.domain.enums.RefundReason;
import com.bidnow.wallet.dto.request.DepositLockRequest;
import com.bidnow.wallet.dto.response.DepositLockResponse;
import com.bidnow.wallet.dto.response.DepositLockStatusResponse;

import java.util.UUID;

public interface DepositLockService {

    DepositLockStatusResponse getDepositLock(UUID userId, UUID auctionId);

    DepositLockResponse lockDeposit(DepositLockRequest request);

    void releaseDeposit(UUID lockId, UUID walletId, RefundReason reason);
}
