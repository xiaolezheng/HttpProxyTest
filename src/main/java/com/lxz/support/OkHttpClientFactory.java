package com.lxz.support;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.apache.OkApacheClient;
import org.apache.http.client.HttpClient;

/**
 * OkHttp 支持http2, http1.1, http1.0自动检测切换, 封装线程池,连接检测等等
 * <p>
 * Created by xiaolezheng on 16/12/4.
 */
public final class OkHttpClientFactory {
    private static final OkHttpClient OK_HTTP_CLIENT = new OkHttpClient();
    private static final OkApacheClient OK_APACHE_CLIENT = new OkApacheClient(OK_HTTP_CLIENT);

    private OkHttpClientFactory() {
    }

    public static HttpClient getHttpClient() {
        return OK_APACHE_CLIENT;
    }
}