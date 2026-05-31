package com.bidnow.common.aop;

import java.util.Optional;

public interface EntityResolver<T> {
    String entityType();

    Optional<T> resolve(String entityId);
}
