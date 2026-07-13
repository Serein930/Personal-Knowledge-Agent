package com.agentmind.chat.repository;

import com.agentmind.chat.model.RagModelCallObservation;
import com.agentmind.chat.model.RagModelCallMetricAggregate;
import com.agentmind.chat.model.RagModelCallStatus;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * 基于关系数据库的模型调用观测记录仓库。
 *
 * <p>该适配器复用项目已有数据源并使用原生数据库访问方式，避免当前阶段额外引入持久化框架。
 * 只有显式把观测存储切换为数据库时才启用。</p>
 */
@Repository
@ConditionalOnProperty(prefix = "agentmind.rag", name = "observation-store", havingValue = "jdbc")
public class JdbcRagModelCallObservationRepository implements RagModelCallObservationRepository {

    private static final String INSERT_SQL = """
            insert into rag_model_call_observations (
                id, workspace_id, prompt_version, answer_generator, model_name,
                citation_count, refused, status, elapsed_millis, answer_length,
                failure_reason, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            select id, workspace_id, prompt_version, answer_generator, model_name,
                   citation_count, refused, status, elapsed_millis, answer_length,
                   failure_reason, created_at
            from rag_model_call_observations
            where workspace_id = ?
            order by created_at desc, id desc
            offset ? limit ?
            """;

    private static final String SELECT_BY_STATUS_SQL = """
            select id, workspace_id, prompt_version, answer_generator, model_name,
                   citation_count, refused, status, elapsed_millis, answer_length,
                   failure_reason, created_at
            from rag_model_call_observations
            where workspace_id = ? and status = ?
            order by created_at desc, id desc
            offset ? limit ?
            """;

    private static final String COUNT_SQL = """
            select count(*) from rag_model_call_observations where workspace_id = ?
            """;

    private static final String COUNT_BY_STATUS_SQL = """
            select count(*) from rag_model_call_observations where workspace_id = ? and status = ?
            """;

    private static final String AGGREGATE_METRICS_SQL = """
            select model_name,
                   prompt_version,
                   count(*) as total_call_count,
                   sum(case when status = 'SUCCEEDED' then 1 else 0 end) as successful_call_count,
                   sum(case when status = 'FALLBACK' then 1 else 0 end) as fallback_call_count,
                   sum(case when status = 'FAILED' then 1 else 0 end) as failed_call_count,
                   sum(case when status = 'CANCELLED' then 1 else 0 end) as cancelled_call_count,
                   sum(elapsed_millis) as total_elapsed_millis
            from rag_model_call_observations
            where workspace_id = ?
            group by model_name, prompt_version
            order by total_call_count desc, model_name, prompt_version
            """;

    private final DataSource dataSource;

    public JdbcRagModelCallObservationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void save(RagModelCallObservation observation) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, observation.id());
            statement.setLong(2, observation.workspaceId());
            statement.setString(3, observation.promptVersion());
            statement.setString(4, observation.answerGenerator());
            statement.setString(5, observation.modelName());
            statement.setInt(6, observation.citationCount());
            statement.setBoolean(7, observation.refused());
            statement.setString(8, observation.status().name());
            statement.setLong(9, observation.elapsedMillis());
            statement.setInt(10, observation.answerLength());
            statement.setString(11, observation.failureReason());
            statement.setObject(12, observation.createdAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("保存模型调用观测记录失败", exception);
        }
    }

    @Override
    public List<RagModelCallObservation> findByWorkspaceId(
            Long workspaceId,
            RagModelCallStatus status,
            int offset,
            int limit
    ) {
        String sql = status == null ? SELECT_SQL : SELECT_BY_STATUS_SQL;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameterIndex = 1;
            statement.setLong(parameterIndex++, workspaceId);
            if (status != null) {
                statement.setString(parameterIndex++, status.name());
            }
            statement.setInt(parameterIndex++, offset);
            statement.setInt(parameterIndex, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<RagModelCallObservation> observations = new ArrayList<>();
                while (resultSet.next()) {
                    observations.add(mapObservation(resultSet));
                }
                return observations;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("查询模型调用观测记录失败", exception);
        }
    }

    @Override
    public long countByWorkspaceId(Long workspaceId, RagModelCallStatus status) {
        String sql = status == null ? COUNT_SQL : COUNT_BY_STATUS_SQL;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workspaceId);
            if (status != null) {
                statement.setString(2, status.name());
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("统计模型调用观测记录失败", exception);
        }
    }

    @Override
    public List<RagModelCallMetricAggregate> aggregateMetricsByWorkspaceId(Long workspaceId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(AGGREGATE_METRICS_SQL)) {
            statement.setLong(1, workspaceId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<RagModelCallMetricAggregate> aggregates = new ArrayList<>();
                while (resultSet.next()) {
                    aggregates.add(new RagModelCallMetricAggregate(
                            resultSet.getString("model_name"),
                            resultSet.getString("prompt_version"),
                            resultSet.getLong("total_call_count"),
                            resultSet.getLong("successful_call_count"),
                            resultSet.getLong("fallback_call_count"),
                            resultSet.getLong("failed_call_count"),
                            resultSet.getLong("cancelled_call_count"),
                            resultSet.getLong("total_elapsed_millis")
                    ));
                }
                return aggregates;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("聚合模型调用指标失败", exception);
        }
    }

    private RagModelCallObservation mapObservation(ResultSet resultSet) throws SQLException {
        return new RagModelCallObservation(
                resultSet.getString("id"),
                resultSet.getLong("workspace_id"),
                resultSet.getString("prompt_version"),
                resultSet.getString("answer_generator"),
                resultSet.getString("model_name"),
                resultSet.getInt("citation_count"),
                resultSet.getBoolean("refused"),
                RagModelCallStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("elapsed_millis"),
                resultSet.getInt("answer_length"),
                resultSet.getString("failure_reason"),
                resultSet.getObject("created_at", java.time.OffsetDateTime.class)
        );
    }
}
