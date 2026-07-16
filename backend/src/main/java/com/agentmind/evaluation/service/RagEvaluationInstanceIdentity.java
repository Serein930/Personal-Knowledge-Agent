package com.agentmind.evaluation.service;

import com.agentmind.evaluation.config.RagEvaluationProperties;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 为当前应用实例生成稳定的评估任务租约标识。
 *
 * <p>生产环境可通过配置显式指定实例编号；本地未配置时使用主机名、进程号和随机后缀组合，
 * 避免同一主机启动多个进程时互相误认租约。</p>
 */
@Component
public class RagEvaluationInstanceIdentity {

    private final String value;

    public RagEvaluationInstanceIdentity(RagEvaluationProperties properties) {
        this.value = StringUtils.hasText(properties.getInstanceId())
                ? properties.getInstanceId().trim()
                : hostName() + ":" + ManagementFactory.getRuntimeMXBean().getName()
                    + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String value() {
        return value;
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "unknown-host";
        }
    }
}
