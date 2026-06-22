package com.taxi.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Operator/admin (staff) yaratish — Feature A. Parol majburiy (BCrypt bilan saqlanadi, hech qachon qaytarilmaydi). */
public class CreateStaffRequest {

    @NotBlank(message = "Ism kerak")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Telefon kerak")
    @Size(max = 15)
    private String phone;

    @NotBlank(message = "Login (username) kerak")
    @Size(max = 50)
    private String username;

    @NotBlank(message = "Parol kerak")
    @Size(max = 100)
    private String password;

    /** OPERATOR yoki ADMIN. */
    @NotBlank(message = "Rol kerak")
    private String role;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
