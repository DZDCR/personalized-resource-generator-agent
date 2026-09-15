package com.example.learning.agent.product;

import java.util.List;

/**
 * CoderAgent 产出的代码实操案例。
 */
public record CodeCaseSet(
        String topic,
        List<CodeCase> cases
) {

    public record CodeCase(
            String language,
            String scene,
            String code,
            String runExample,
            String variation
    ) {}
}