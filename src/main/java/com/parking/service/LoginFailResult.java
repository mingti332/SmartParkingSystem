package com.parking.service;

public class LoginFailResult {
    private final boolean success;
    private final String message;
    private final int remainingAttempts;

    private LoginFailResult(boolean success, String message, int remainingAttempts) {
        this.success = success;
        this.message = message;
        this.remainingAttempts = remainingAttempts;
    }

    public static LoginFailResult success() {
        return new LoginFailResult(true, null, -1);
    }

    public static LoginFailResult fail(String message, int remainingAttempts) {
        return new LoginFailResult(false, message, remainingAttempts);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public int getRemainingAttempts() {
        return remainingAttempts;
    }
}