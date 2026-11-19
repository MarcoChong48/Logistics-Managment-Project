package com.lms.backend.shared.login;

/** The username and exact password entered at login. */
public record SharedLoginUserRequest(String sharedLoginUserUsername, String sharedLoginUserPassword) {
}
