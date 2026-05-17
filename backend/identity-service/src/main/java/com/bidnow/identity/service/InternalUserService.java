package com.bidnow.identity.service;

import java.util.List;
import java.util.UUID;

public interface InternalUserService {
    List<String> getActiveUserEmails();

    List<String> getEmailsByUserIds(List<UUID> userIds);
}
