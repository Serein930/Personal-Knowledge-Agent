package com.agentmind.common.security.proxy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * 只处理 IP 字面量的 CIDR 匹配器。
 *
 * <p>配置阶段拒绝主机名，避免安全校验因为 DNS 变化而改变可信边界。IPv4 与 IPv6 都按照网络前缀逐位比较，
 * 候选地址和网络地址族不一致时直接判定为不匹配。</p>
 */
final class IpCidrMatcher {

    private final List<Network> networks;

    IpCidrMatcher(List<String> cidrs) {
        this.networks = new ArrayList<>();
        for (String cidr : cidrs) {
            if (cidr != null && !cidr.isBlank()) {
                networks.add(parse(cidr.trim()));
            }
        }
        if (networks.isEmpty()) {
            throw new IllegalArgumentException("可信代理网段不能为空");
        }
    }

    boolean matches(String candidateAddress) {
        byte[] candidate = parseLiteral(candidateAddress, "代理来源地址不是有效 IP 字面量");
        return networks.stream().anyMatch(network -> network.matches(candidate));
    }

    private Network parse(String cidr) {
        String[] parts = cidr.split("/", -1);
        if (parts.length > 2 || parts[0].isBlank()) {
            throw new IllegalArgumentException("无效的可信代理网段：" + cidr);
        }
        byte[] address = parseLiteral(parts[0], "可信代理网段必须使用 IP 字面量：" + cidr);
        int maximumPrefix = address.length * Byte.SIZE;
        int prefix = parts.length == 1 ? maximumPrefix : parsePrefix(parts[1], maximumPrefix, cidr);
        return new Network(address, prefix);
    }

    private int parsePrefix(String value, int maximumPrefix, String cidr) {
        try {
            int prefix = Integer.parseInt(value);
            if (prefix < 0 || prefix > maximumPrefix) {
                throw new IllegalArgumentException("可信代理网段前缀越界：" + cidr);
            }
            return prefix;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("可信代理网段前缀无效：" + cidr, exception);
        }
    }

    private byte[] parseLiteral(String value, String message) {
        if (value == null || value.isBlank() || !value.matches("[0-9a-fA-F:.]+")) {
            throw new IllegalArgumentException(message);
        }
        try {
            return InetAddress.getByName(value).getAddress();
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(message, exception);
        }
    }

    private record Network(byte[] address, int prefixLength) {

        private boolean matches(byte[] candidate) {
            if (candidate.length != address.length) {
                return false;
            }
            int completeBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            for (int index = 0; index < completeBytes; index++) {
                if (candidate[index] != address[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (candidate[completeBytes] & mask) == (address[completeBytes] & mask);
        }
    }
}
