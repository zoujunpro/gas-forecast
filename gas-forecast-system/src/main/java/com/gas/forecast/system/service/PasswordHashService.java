package com.gas.forecast.system.service;

public interface PasswordHashService {
    String hash(String username, String password, String salt);
}
