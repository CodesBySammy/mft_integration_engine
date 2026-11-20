package com.mft.integration.layout;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding pre-defined and dynamic file layout contracts.
 */
@Component
public class LayoutRegistry {

    public static final String POSITION_LAYOUT_ID = "POSITION_FIXED_WIDTH";

    private final Map<String, LayoutDefinition> registry = new ConcurrentHashMap<>();

    public LayoutRegistry(FixedWidthLayoutParser parser) {
    }

    @PostConstruct
    public void initialize() {
        // Register standard position fixed-width layout (Section 16 & 17)
        // Total record length: 41 bytes
        // tradeId: positions 1-12
        // currency: positions 13-15
        // amount: positions 16-33
        // businessDate: positions 34-41
        List<FieldDefinition> positionFields = List.of(
                new FieldDefinition("tradeId", 1, 12, true),
                new FieldDefinition("currency", 13, 3, true),
                new FieldDefinition("amount", 16, 18, true),
                new FieldDefinition("businessDate", 34, 8, true)
        );

        LayoutDefinition positionLayout = new LayoutDefinition(
                POSITION_LAYOUT_ID,
                41,
                StandardCharsets.US_ASCII,
                positionFields
        );

        register(positionLayout);
    }

    public void register(LayoutDefinition layout) {
        registry.put(layout.getLayoutId(), layout);
    }

    public LayoutDefinition getLayout(String layoutId) {
        return registry.get(layoutId);
    }
}
