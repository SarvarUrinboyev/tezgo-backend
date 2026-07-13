package com.taxi.backend.controller;

import com.taxi.backend.repository.DriverRepository;
import com.taxi.backend.repository.UserRepository;
import com.taxi.backend.security.JwtFilter;
import com.taxi.backend.security.JwtService;
import com.taxi.backend.security.SecurityConfig;
import com.taxi.backend.service.PaymentService;
import com.taxi.backend.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Security regression tests for the two Click Shop API callback routes. */
@WebMvcTest(controllers = PaymentController.class)
@Import({SecurityConfig.class, JwtFilter.class})
class PaymentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private PaymentService paymentService;
    @MockitoBean private DriverRepository driverRepository;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private TokenBlacklistService tokenBlacklistService;
    @MockitoBean private StringRedisTemplate redis;

    @Test
    void clickCallbacksAcceptUnauthenticatedFormPostsWithoutRedirect() throws Exception {
        when(paymentService.handleClickPrepare(anyMap())).thenReturn(Map.of("error", 0));
        when(paymentService.handleClickComplete(anyMap())).thenReturn(Map.of("error", 0));

        mockMvc.perform(post("/api/payment/click/prepare")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "synthetic-prepare"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/payment/click/complete")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("click_trans_id", "synthetic-complete"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void paymentCreationAndDriverOrderStatusRemainProtectedWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/payment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100000}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/payment/click/orders/other-drivers-order"))
                .andExpect(status().isForbidden());
    }
}
