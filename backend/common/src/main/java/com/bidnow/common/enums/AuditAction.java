package com.bidnow.common.enums;

/**
 * Represents the type of action performed in an audit log entry.
 */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    STATE_CHANGE,
    LOGIN,
    LOGOUT,
    ADMIN_ACTION
}
