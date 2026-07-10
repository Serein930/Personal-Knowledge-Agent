package com.agentmind.knowledge.repository;

import java.util.Locale;

/**
 * 数据库向量扩展值使用的轻量结构化查询语句辅助工具。
 *
 * <p>数据库向量扩展接受向量字面量格式。把转换逻辑集中到一个工具类中，
 * 可以避免在原生数据库访问适配器中散落字符串格式化逻辑，后续也方便替换为专用向量类型。</p>
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
