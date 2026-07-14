package com.agentmind.agent.tool;

import com.agentmind.common.exception.BusinessException;
import com.agentmind.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

/**
 * 工具参数读取辅助类。
 *
 * <p>工具调用入参来自前端或模型，不能直接假定字段类型正确。统一在这里完成对象形态、必填字段、数值范围等校验，
 * 避免每个工具各自实现不一致的字符串解析逻辑。</p>
 */
public final class AgentToolArgumentReader {

    private AgentToolArgumentReader() {
    }

    public static void requireObject(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "工具参数必须是 JSON 对象");
        }
    }

    public static String requireText(JsonNode arguments, String fieldName, int maxLength) {
        requireObject(arguments);
        JsonNode field = arguments.get(fieldName);
        if (field == null || !field.isTextual() || !StringUtils.hasText(field.asText())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + "不能为空");
        }
        String value = field.asText().trim();
        if (value.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + "长度不能超过" + maxLength);
        }
        return value;
    }

    public static long requirePositiveLong(JsonNode arguments, String fieldName) {
        requireObject(arguments);
        JsonNode field = arguments.get(fieldName);
        if (field == null || !field.isIntegralNumber() || !field.canConvertToLong() || field.asLong() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + "必须为正整数");
        }
        return field.asLong();
    }

    public static String optionalText(JsonNode arguments, String fieldName, int maxLength) {
        requireObject(arguments);
        JsonNode field = arguments.get(fieldName);
        if (field == null || field.isNull()) {
            return "";
        }
        if (!field.isTextual()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + "必须为字符串");
        }
        String value = field.asText().trim();
        if (value.length() > maxLength) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + "长度不能超过" + maxLength);
        }
        return value;
    }

    public static int optionalInteger(JsonNode arguments, String fieldName, int defaultValue, int min, int max) {
        requireObject(arguments);
        JsonNode field = arguments.get(fieldName);
        if (field == null || field.isNull()) {
            return defaultValue;
        }
        if (!field.isIntegralNumber() || !field.canConvertToInt()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, fieldName + "必须为整数");
        }
        int value = field.asInt();
        if (value < min || value > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    fieldName + "必须在" + min + "到" + max + "之间");
        }
        return value;
    }
}
