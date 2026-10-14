package com.bidnow.wallet.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminWalletDetailResponse {
    private AdminWalletResponse wallet;
    private List<AdminTransactionResponse> recentTransactions;
    private List<AdminDepositLockResponse> activeDepositLocks;
}
