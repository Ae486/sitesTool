package com.rpacloud.proxy.provider;

import java.util.List;

public interface ProxyProviderAdapter {

    List<ProxyInfo> fetch(String protocol, int count, String countryCode);

    record ProxyInfo(String ip, int port, String protocol) {}
}
