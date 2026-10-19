package com.example.ticketsystem.controller;

import com.example.ticketsystem.service.DeveloperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeveloperControllerTest {
    private DeveloperService service;
    private MockMvc mvc;
    @BeforeEach void setup() {
        service = mock(DeveloperService.class);
        mvc = MockMvcBuilders.standaloneSetup(new DeveloperController(service)).build();
    }
    @Test void explicitConfirmationRequired() throws Exception {
        mvc.perform(post("/api/developer/reset").contentType("application/json").content("{\"eventId\":1,\"stock\":20,\"expectedOrders\":7,\"confirmed\":false}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void unrelatedWebsiteCannotTriggerReset() throws Exception {
        mvc.perform(post("/api/developer/reset").header("Origin", "https://other.example").contentType("application/json")
                        .content("{\"eventId\":1,\"stock\":20,\"expectedOrders\":7,\"confirmed\":true}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void getDoesNotResetAndFormPostIsRejected() throws Exception {
        mvc.perform(get("/api/developer/reset")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/developer/reset").contentType("application/x-www-form-urlencoded").content("confirmed=true"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }
    @Test void confirmationDirectlyPassesSelectedEventAndStock() throws Exception {
        mvc.perform(post("/api/developer/reset").contentType("application/json")
                        .content("{\"eventId\":1,\"stock\":20,\"expectedOrders\":7,\"confirmed\":true}"))
                .andExpect(status().isOk());
        verify(service).reset(1, 20, 7);
    }
    @Test void toolsAreNotExposedUnlessExplicitlyEnabled() {
        new ApplicationContextRunner().withUserConfiguration(DeveloperController.class)
                .withBean(DeveloperService.class, () -> service)
                .run(context -> assertEquals(0, context.getBeanNamesForType(DeveloperController.class).length));
    }
    @Test void capabilitiesIncludeResetAvailabilityAndLimits() throws Exception {
        when(service.resetAvailable()).thenReturn(true);
        mvc.perform(get("/api/developer/capabilities"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.resetAvailable").value(true))
                .andExpect(jsonPath("$.maxRequests").value(500))
                .andExpect(jsonPath("$.maxConcurrency").value(10));
    }
    @Test void statePassesEventAndRunPrefixToService() throws Exception {
        var snapshot = new DeveloperService.Snapshot(1, "test", 20, 18, 18, 2, "OPEN", 2, 1, "now");
        when(service.snapshot(1, "demo-abcdef123456-")).thenReturn(snapshot);
        mvc.perform(get("/api/developer/events/1/state").param("runPrefix", "demo-abcdef123456-"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.eventId").value(1))
                .andExpect(jsonPath("$.runOrders").value(1));
        verify(service).snapshot(1, "demo-abcdef123456-");
    }
    @Test void trustedOriginCanSubmitConfirmedReset() throws Exception {
        mvc.perform(post("/api/developer/reset").header("Origin", "http://ticket.local")
                        .contentType("application/json")
                        .content("{\"eventId\":1,\"stock\":20,\"expectedOrders\":7,\"confirmed\":true}"))
                .andExpect(status().isOk());
        verify(service).reset(1, 20, 7);
    }
}
