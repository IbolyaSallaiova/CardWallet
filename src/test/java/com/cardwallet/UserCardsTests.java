package com.cardwallet;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserCardsTests extends AbstractTest {

    @Test
    void adminDashboard_showsUserListAndNoCards() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/login/secure")
                .param("username", "admin")
                .param("password", "admin123")
                .session(session));

        String body = mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("ibi");
        assertThat(body).contains("anna");
        assertThat(body).doesNotContain("Café Luma");
        assertThat(body).doesNotContain("5391 2240 7781 4821");
        assertThat(body).doesNotContain("card-tile");
    }

    @Test
    void userDashboard_showsOwnCardsOnly() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/login/secure")
                .param("username", "ibi")
                .param("password", "ibi123")
                .session(session));

        String body = mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(body).contains("Everyday");
        assertThat(body).contains("Travel");
        assertThat(body).doesNotContain("Student");
    }
}
