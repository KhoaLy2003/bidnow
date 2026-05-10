/*
 * BidNow Auction System
 */
package com.bidnow.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private UUID userId;
    private String displayName;
    private String avatarUrl;
    private String phoneNumber;
    private String address;
    private String city;
    private String country;
    private String postalCode;
    private String bio;

    private List<String> roles;

    private String language;
    private String timezone;
    private String currency;
    private Boolean emailNotifications;
    private Boolean pushNotifications;
    private Boolean smsNotifications;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
