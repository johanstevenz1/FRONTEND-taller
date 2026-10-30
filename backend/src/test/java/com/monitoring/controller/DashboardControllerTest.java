package com.monitoring.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.monitoring.store.LogStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardControllerTest {
    @Test
    void rendersJavaServedDashboardWithPollingAndLoadAction() throws Exception {
        LogStore store = new LogStore();
        MetricsController metrics = new MetricsController(store, Map.of());
        DashboardController dashboard = new DashboardController(metrics, store, new ObjectMapper());
        ModelAndView modelAndView = dashboard.dashboard("", "", "", "", "", 0);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        modelAndView.getView().render(modelAndView.getModel(), request, response);

        String html = response.getContentAsString();
        assertEquals("text/html;charset=UTF-8", response.getContentType());
        assertTrue(html.contains("Microservices Monitoring Dashboard"));
        assertTrue(html.contains("content=\"3;url=/"));
        assertTrue(html.contains("Simulate load"));
        assertTrue(html.contains("No logs yet. Click 'Simulate load'."));
    }
}
