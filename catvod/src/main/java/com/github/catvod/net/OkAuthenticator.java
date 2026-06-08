package com.github.catvod.net;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Auth;
import com.github.catvod.utils.Util;
import com.google.common.net.HttpHeaders;

import java.net.InetSocketAddress;
import java.util.Objects;

import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

public class OkAuthenticator implements Authenticator {

    private final OkProxySelector selector;

    public OkAuthenticator(OkProxySelector selector) {
        this.selector = selector;
    }

    @Nullable
    @Override
    public Request authenticate(@Nullable Route route, @NonNull Response response) {
        if (route == null || response.request().header(HttpHeaders.PROXY_AUTHORIZATION) != null) return null;
        if (!(route.proxy().address() instanceof InetSocketAddress proxyAddress)) return null;
        String userInfo = findUserInfo(response.request().url().host(), proxyAddress.getHostName());
        return userInfo == null ? null : response.request().newBuilder().header(HttpHeaders.PROXY_AUTHORIZATION, Auth.basic(userInfo)).build();
    }

    private String findUserInfo(String requestHost, String proxyHost) {
        return selector.getProxy().stream().filter(item -> matchesHost(item, requestHost)).map(item -> item.getUserInfo(proxyHost, "http")).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private boolean matchesHost(Proxy item, String requestHost) {
        return item.getHosts().stream().anyMatch(host -> Util.containOrMatch(requestHost, host));
    }
}
