package com.agentmind.knowledge.repository;

import java.util.Locale;

/**
 * Small SQL helper for pgvector values.
 *
 * <p>pgvector accepts vector literals in the form `[0.1,0.2,...]`. Keeping the conversion in one helper avoids
 * scattering string formatting across the JDBC adapter and makes it easy to replace with a dedicated pgvector JDBC
 * type later.</p>
 */
public final class PgVectorSqlSupport {

    private PgVectorSqlSupport() {
    }

    public static String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%.8f", embedding[index]));
        }
        return builder.append(']').toString();
    }
}
