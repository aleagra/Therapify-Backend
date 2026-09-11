package com.example.therapify.dtos.UserDTOs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frontend reads `isDemo` by that exact name; without the explicit @JsonProperty Jackson
 * strips the "is" prefix and emits "demo". Pin the wire name so a refactor can't break it.
 */
class UserDetailDTOJsonTest {

    @Test
    void serializesIsDemoWithTheIsPrefix() throws Exception {
        UserDetailDTO dto = new UserDetailDTO(
                1L, "Paciente", "Demo", "demo.paciente@therapify.com", "PACIENTE",
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, true
        );

        String json = new ObjectMapper().writeValueAsString(dto);

        assertTrue(json.contains("\"isDemo\":true"), "JSON inesperado: " + json);
    }
}
