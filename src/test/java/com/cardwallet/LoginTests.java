package com.cardwallet;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginTests extends AbstractTest {

    @Test
    void loginSuccess_validCredentials() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login/secure")
                        .param("username", "admin")
                        .param("password", "admin123")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/dashboard"));

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void loginFailure_wrongPassword() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login/secure")
                        .param("username", "admin")
                        .param("password", "wrongpassword")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login"));

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login"));
    }

    @Test
    void vulnerableBypass_succeeds() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login/vulnerable")
                        .param("username", "admin' --")
                        .param("password", "anything")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/dashboard"));

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk());
    }

    @Test
    void secureRejects_bypass() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login/secure")
                        .param("username", "admin' --")
                        .param("password", "anything")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login"));

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void dashboard_requiresAuth() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login"));
    }

    @Test
    void logout_clearsSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login/secure")
                .param("username", "admin")
                .param("password", "admin123")
                .session(session));

        mockMvc.perform(get("/dashboard").session(session)).andExpect(status().isOk());

        mockMvc.perform(get("/logout").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login"));

        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void modeSwitch_clearsSessionAndSetsNewMode() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/login/secure")
                .param("username", "admin")
                .param("password", "admin123")
                .session(session));

        mockMvc.perform(get("/dashboard").session(session)).andExpect(status().isOk());

        MvcResult switchResult = mockMvc.perform(post("/mode/vulnerable").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/login"))
                .andReturn();

        // The old session is invalidated by the mode switch.
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().is3xxRedirection());

        MockHttpSession newSession = (MockHttpSession) switchResult.getRequest().getSession(false);
        assertThat(newSession).isNotNull();
        assertThat(newSession.getAttribute("mode")).isEqualTo("vulnerable");

        mockMvc.perform(get("/login").session(newSession))
                .andExpect(status().isOk());
    }

    @Test
    void modeRoute_rejectsArbitraryValues() throws Exception {
        mockMvc.perform(post("/mode/haxor"))
                .andExpect(status().isNotFound());
    }
}
