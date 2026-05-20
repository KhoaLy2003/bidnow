package com.bidnow.common.util;

public class AuditContextHolder {
    private static final ThreadLocal<Object> OLD_STATE = new ThreadLocal<>();
    private static final ThreadLocal<Object> NEW_STATE = new ThreadLocal<>();

    public static void setOldState(Object state) {
        OLD_STATE.set(state);
    }

    public static Object getOldState() {
        return OLD_STATE.get();
    }

    public static void setNewState(Object state) {
        NEW_STATE.set(state);
    }

    public static Object getNewState() {
        return NEW_STATE.get();
    }

    public static void clear() {
        OLD_STATE.remove();
        NEW_STATE.remove();
    }
}
