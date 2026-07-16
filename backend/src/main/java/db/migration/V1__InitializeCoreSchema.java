package db.migration;

import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.core.io.ClassPathResource;

/**
 * 将项目早期阶段积累的幂等建表脚本纳入 Flyway 基线。
 *
 * <p>已有本地数据库可能已经手工执行过这些脚本，因此基线迁移继续复用其中的
 * {@code if not exists} 和增量 {@code alter} 语义；新数据库则会一次性建立完整核心表结构。</p>
 */
public class V1__InitializeCoreSchema extends BaseJavaMigration {

    private static final List<String> SCHEMA_RESOURCES = List.of(
            "db/schema/knowledge_vector_chunks.sql",
            "db/schema/rag_model_call_observations.sql",
            "db/schema/agent_write_tools.sql",
            "db/schema/rag_evaluations.sql"
    );

    @Override
    public void migrate(Context context) throws Exception {
        for (String resourcePath : SCHEMA_RESOURCES) {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            String sql;
            try (var inputStream = resource.getInputStream()) {
                sql = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            // 整段交给 PostgreSQL 原生解析，确保函数体内的分号不会被通用脚本工具误拆。
            try (Statement statement = context.getConnection().createStatement()) {
                statement.execute(sql);
            }
        }
    }
}
