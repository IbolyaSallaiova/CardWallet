package com.cardwallet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SqlInjectionTests extends AbstractTest {

    private static final String TAUTOLOGY_PAYLOAD = "' OR '1'='1' --";
    private static final String ANNA_CARD_NUMBER = "5500 1123 6689 0021";

    private final ObjectMapper mapper = new ObjectMapper();

    private List<String> cardNumbersFromSearch(MockHttpSession session, String term) throws Exception {
        String body = mockMvc.perform(post("/search")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"term\":\"" + term.replace("\"", "\\\"") + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode results = mapper.readTree(body).get("results");
        List<String> cardNumbers = new ArrayList<>();
        for (JsonNode row : results) {
            cardNumbers.add(row.get(1).asText());
        }
        return cardNumbers;
    }

    @Test
    void tautologyAttack_succeedsInVulnerableMode() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/login/vulnerable")
                .param("username", "ibi")
                .param("password", "ibi123")
                .session(session));

        List<String> normal = cardNumbersFromSearch(session, "5391");
        assertThat(normal).anyMatch(s -> s.contains("5391"));

        List<String> attack = cardNumbersFromSearch(session, TAUTOLOGY_PAYLOAD);
        assertThat(attack).contains(ANNA_CARD_NUMBER);
    }

    @Test
    void tautologyAttack_failsInSecureMode() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/login/secure")
                .param("username", "ibi")
                .param("password", "ibi123")
                .session(session));

        List<String> attack = cardNumbersFromSearch(session, TAUTOLOGY_PAYLOAD);
        assertThat(attack).doesNotContain(ANNA_CARD_NUMBER);
        assertThat(attack).isEmpty();
    }

    @Test
    void search_requiresAuth() throws Exception {
        mockMvc.perform(post("/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"term\":\"4821\"}"))
                .andExpect(status().is3xxRedirection());
    }
}
