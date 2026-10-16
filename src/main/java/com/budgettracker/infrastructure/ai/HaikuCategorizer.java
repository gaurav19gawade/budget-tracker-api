package com.budgettracker.infrastructure.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.budgettracker.application.port.AutoCategorizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HaikuCategorizer implements AutoCategorizer {

    private static final Logger log = LoggerFactory.getLogger(HaikuCategorizer.class);
    private static final int BATCH_SIZE = 30;
    private static final TypeReference<List<Map<String, String>>> LIST_MAP_REF = new TypeReference<>() {};

    private final AnthropicClient client;
    private final ObjectMapper mapper;
    private final boolean enabled;

    public HaikuCategorizer(@Value("${app.anthropic.api-key:}") String apiKey,
                             ObjectMapper mapper) {
        this.mapper = mapper;
        if (apiKey == null || apiKey.isBlank()) {
            this.client = null;
            this.enabled = false;
            log.info("ANTHROPIC_API_KEY not configured — AI rule suggestions disabled.");
        } else {
            this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
            this.enabled = true;
        }
    }

    @Override
    public List<Suggestion> suggest(List<String> payees, List<String> categoryNames) {
        if (!enabled || payees.isEmpty()) return List.of();

        List<Suggestion> results = new ArrayList<>();
        for (int i = 0; i < payees.size(); i += BATCH_SIZE) {
            List<String> batch = payees.subList(i, Math.min(i + BATCH_SIZE, payees.size()));
            try {
                results.addAll(classifyBatch(batch, categoryNames));
            } catch (Exception e) {
                log.warn("AI categorization failed for batch of {} payees: {}", batch.size(), e.getMessage());
            }
        }
        return results;
    }

    private List<Suggestion> classifyBatch(List<String> payees, List<String> categoryNames)
            throws Exception {
        String categoriesLine = String.join(", ", categoryNames);
        String payeeLines = String.join("\n", payees);

        String prompt = """
                Categories: %s

                Classify each payee below into exactly one of those categories.
                Respond ONLY with a JSON array — no markdown fences, no explanation.
                Each element must be: {"payee": "...", "category": "..."}
                Use "Uncategorized" if none of the categories fit.

                Payees:
                %s""".formatted(categoriesLine, payeeLines);

        var params = MessageCreateParams.builder()
                .model(Model.CLAUDE_HAIKU_4_5)
                .maxTokens(1024L)
                .addUserMessage(prompt)
                .build();

        var message = client.messages().create(params);

        String json = message.content().stream()
                .flatMap(b -> b.text().stream())
                .map(t -> t.text())
                .findFirst()
                .orElse("[]")
                .trim();

        // Strip markdown code fences in case the model wraps the response
        if (json.startsWith("```")) {
            json = json.replaceAll("(?s)^```[a-zA-Z]*\\n?", "").replaceAll("\\n?```$", "").trim();
        }

        List<Map<String, String>> items = mapper.readValue(json, LIST_MAP_REF);
        return items.stream()
                .filter(m -> m.get("payee") != null && m.get("category") != null)
                .map(m -> new Suggestion(m.get("payee"), m.get("category")))
                .toList();
    }
}
