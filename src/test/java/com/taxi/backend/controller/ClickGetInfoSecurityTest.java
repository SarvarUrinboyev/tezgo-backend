package com.taxi.backend.controller;

import com.taxi.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taxi.backend.security.JwtFilter;
import com.taxi.backend.security.JwtService;
import com.taxi.backend.security.SecurityConfig;
import com.taxi.backend.service.ClickGetInfoService;
import com.taxi.backend.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdvancedShopController.class)
@Import({SecurityConfig.class, JwtFilter.class, ClickGetInfoExceptionHandler.class, ClickGetInfoRequestSizeFilter.class})
class ClickGetInfoSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ClickGetInfoService clickGetInfoService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private TokenBlacklistService tokenBlacklistService;
    @MockitoBean private StringRedisTemplate redis;

    @Test
    void onlyTypedGetInfoIsPublicAndOldShopMoneyRouteIsDenied() throws Exception {
        when(clickGetInfoService.handle(any(), any(), any())).thenReturn(Map.of(
                "error", 0, "error_note", "Success", "params", Map.of("full_name", "Test Driver")));

        mockMvc.perform(post("/api/payment/click-shop/getinfo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":0,\"service_id\":\"105926\",\"params\":{\"account\":\"TZ-0005\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.params.full_name").value("Test Driver"));
        verify(clickGetInfoService).handle(any(), any(), any());

        mockMvc.perform(post("/api/payment/click-shop/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedWrongMediaAndOversizedBodiesUseOnlyTheProtocolErrorSchema() throws Exception {
        mockMvc.perform(post("/api/payment/click-shop/getinfo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(-8))
                .andExpect(jsonPath("$.error_note").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/payment/click-shop/getinfo")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value(-8))
                .andExpect(jsonPath("$.error_note").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/payment/click-shop/getinfo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("x".repeat(ClickGetInfoRequestSizeFilter.MAX_CONTENT_LENGTH + 1)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value(-8))
                .andExpect(jsonPath("$.error_note").value("MALFORMED_REQUEST"));

        verify(clickGetInfoService, never()).handle(any(), any(), any());
    }

    @Test
    void chunkedStyleOversizedBodyIsBoundedEvenWithoutADeclaredContentLength() throws Exception {
        ClickGetInfoRequestSizeFilter filter = new ClickGetInfoRequestSizeFilter(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payment/click-shop/getinfo") {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("x".repeat(ClickGetInfoRequestSizeFilter.MAX_CONTENT_LENGTH + 1)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("oversized request must not reach MVC");
        });

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("MALFORMED_REQUEST"));
    }
}
