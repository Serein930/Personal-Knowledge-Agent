package com.agentmind.knowledge.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PgVectorSqlSupportTests {

    @Test
    void toVectorLiteralShouldUsePgVectorLiteralFormat() {
        String literal = PgVectorSqlSupport.toVectorLiteral(new float[] {1.0f, 0.5f, -0.25f});

        assertThat(literal).isEqualTo("[1.00000000,0.50000000,-0.25000000]");
    }
}
