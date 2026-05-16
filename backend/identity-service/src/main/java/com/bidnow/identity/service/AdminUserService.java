package com.bidnow.identity.service;

import com.bidnow.common.constant.ErrorCodes;
import com.bidnow.common.dto.PageResponse;
import com.bidnow.common.exception.NotFoundException;
import com.bidnow.common.util.PaginationUtils;
import com.bidnow.identity.domain.entity.User;
import com.bidnow.identity.dto.request.UpdateUserStatusRequest;
import com.bidnow.identity.dto.response.AdminUserResponse;
import com.bidnow.identity.mapper.UserMapper;
import com.bidnow.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public PageResponse<AdminUserResponse> getAllUsers(int page, int size, String sortBy, String direction) {
        Pageable pageable = PaginationUtils.getPageable(page, size, sortBy, direction);
        Page<User> userPage = userRepository.findAll(pageable);

        List<AdminUserResponse> content = userPage.getContent().stream()
                .map(userMapper::toAdminResponse)
                .toList();

        return PaginationUtils.toPageResponse(userPage, content);
    }

    @Transactional
    public AdminUserResponse updateUserStatus(UUID targetUserId, UUID adminId, UpdateUserStatusRequest request) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found with id: " + targetUserId, ErrorCodes.NOT_FOUND));

        log.info("Admin {} updating status of user {} to {}", adminId, targetUserId, request.getStatus());

        user.setAccountStatus(request.getStatus());
        user.setStatusReason(request.getReason());

        // If banned or suspended, we might want to revoke tokens, but for now we just update status
        // and let the filter handle the status check on next request.

        userRepository.save(user);

        return userMapper.toAdminResponse(user);
    }
}
