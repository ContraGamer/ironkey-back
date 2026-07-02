package com.lionfinance.ironkey.controller;

import com.lionfinance.ironkey.api.controller.HealthController;
import com.lionfinance.ironkey.security.filter.JwtAuthenticationFilter;
import com.lionfinance.ironkey.security.filter.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// excludeFilters: @WebMvcTest detecta automáticamente cualquier bean Filter (incluidos
// nuestros filtros de seguridad), que no pueden resolver sus dependencias en este slice.
@WebMvcTest(
        value = HealthController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, RateLimitFilter.class}
        )
)
class HealthControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void health_returnsUpStatus() throws Exception {
        mvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("ironkey"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
