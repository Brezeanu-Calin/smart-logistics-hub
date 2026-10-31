package ro.logistics.smart_logistics.assistant.api;

import ro.logistics.smart_logistics.assistant.application.AssistantService;
import ro.logistics.smart_logistics.assistant.dto.AssistantChatRequest;
import ro.logistics.smart_logistics.assistant.dto.AssistantChatResponse;
import ro.logistics.smart_logistics.assistant.dto.AssistantConfigResponse;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @GetMapping("/config")
    public AssistantConfigResponse config() {
        return assistantService.config();
    }

    @PostMapping("/chat")
    public AssistantChatResponse chat(@Valid @RequestBody AssistantChatRequest request) {
        return assistantService.chat(request);
    }
}
