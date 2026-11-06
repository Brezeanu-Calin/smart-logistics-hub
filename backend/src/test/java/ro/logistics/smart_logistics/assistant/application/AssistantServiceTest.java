package ro.logistics.smart_logistics.assistant.application;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssistantServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesPlainResponseWithUnrecognizedMetadata() throws Exception {
        var response = AssistantService.parseReply(
                """
                        {"message":"Please provide the vehicle details.","action":null,"metadata":"ignored"}
                        """,
                objectMapper
        );

        assertEquals("Please provide the vehicle details.", response.message());
        assertNull(response.action());
    }

    @Test
    void parsesActionResponseWithExtraModelFields() throws Exception {
        var response = AssistantService.parseReply(
                """
                        {"message":"Add this driver?","action":{"type":"CREATE_DRIVER","summary":"Add Marc","id":null,"payload":{"name":"Marc Tariceanu","email":"marcdriver@email.com","licenseCategories":["C","CE"]},"reasoning":"ignored"}}
                        """,
                objectMapper
        );

        assertEquals("CREATE_DRIVER", response.action().type());
        assertEquals("Marc Tariceanu", response.action().payload().path("name").asText());
    }

    @Test
    void parsesJsonWrappedInMarkdownAndExplanatoryText() throws Exception {
        var response = AssistantService.parseReply(
                """
                        Here is my response:
                        ```json
                        {"message":"Please provide the vehicle details.","action":null}
                        ```
                        """,
                objectMapper
        );

        assertEquals("Please provide the vehicle details.", response.message());
        assertNull(response.action());
    }

    @Test
    void parsesJsonEncodedAsAString() throws Exception {
        String encodedResponse = objectMapper.writeValueAsString(
                "{\"message\":\"Please provide the vehicle details.\",\"action\":null}"
        );

        var response = AssistantService.parseReply(encodedResponse, objectMapper);

        assertEquals("Please provide the vehicle details.", response.message());
    }

    @Test
    void rejectsResponseWithoutMessageOrWithInvalidActionShape() {
        assertThrows(IllegalArgumentException.class, () ->
                AssistantService.parseReply("{\"action\":null}", objectMapper));
        assertThrows(IllegalArgumentException.class, () ->
                AssistantService.parseReply(
                        "{\"message\":\"Proposal\",\"action\":{\"type\":\"CREATE_DRIVER\",\"summary\":\"Add driver\",\"id\":null,\"payload\":[]}}",
                        objectMapper
                ));
    }
}
