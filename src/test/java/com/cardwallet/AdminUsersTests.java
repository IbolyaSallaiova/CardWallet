package com.cardwallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUsersTests extends AbstractTest {

    private static final int ADMIN_ID = 1;
    private static final int IBI_ID = 2;

    private final ObjectMapper mapper = new ObjectMapper();

    private MockHttpSession loginAs(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/login/secure")
                .param("username", username)
                .param("password", password)
                .session(session));
        return session;
    }

    @Test
    void admin_canAddAndSeeNewUser() throws Exception {
        MockHttpSession session = loginAs("admin", "admin123");

        mockMvc.perform(post("/admin/users")
                .session(session)
                .param("username", "zed")
                .param("password", "zed123")
                .param("role", "user"));

        String body = mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("zed");
    }

    @Test
    void admin_canDeleteUser_removesCardsAndPayments() throws Exception {
        MockHttpSession session = loginAs("admin", "admin123");

        mockMvc.perform(post("/admin/users/" + IBI_ID + "/delete").session(session));

        String body = mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("ibi");

        MockHttpSession deletedSession = new MockHttpSession();
        mockMvc.perform(post("/login/secure")
                        .param("username", "ibi")
                        .param("password", "ibi123")
                        .session(deletedSession))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/dashboard").session(deletedSession))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void admin_cannotDeleteSelf() throws Exception {
        MockHttpSession session = loginAs("admin", "admin123");

        mockMvc.perform(post("/admin/users/" + ADMIN_ID + "/delete").session(session));

        String body = mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("admin");
    }

    @Test
    void nonAdmin_cannotAddUser() throws Exception {
        MockHttpSession session = loginAs("ibi", "ibi123");

        mockMvc.perform(post("/admin/users")
                .session(session)
                .param("username", "hacker")
                .param("password", "hacker123")
                .param("role", "admin"));

        MockHttpSession adminSession = loginAs("admin", "admin123");
        String body = mockMvc.perform(get("/dashboard").session(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("hacker");
    }

    @Test
    void adminSearch_returnsNothing() throws Exception {
        MockHttpSession session = loginAs("admin", "admin123");

        String body = mockMvc.perform(post("/search")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"term\":\"4\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode results = mapper.readTree(body).get("results");
        assertThat(results.isEmpty()).isTrue();
    }
}
