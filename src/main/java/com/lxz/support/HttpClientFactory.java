package com.lxz.support;


import org.apache.http.HttpResponse;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.UnknownHostException;

/**
 * Created by xiaolezheng on 16/11/23.
 */
public final class HttpClientFactory {
    private static Logger logger = LoggerFactory.getLogger(HttpClientFactory.class);

    /**
     * 连接超时时间 可以配到配置文件 （单位毫秒）
     */
    private static int MAX_TIME_OUT = 1000 * 5;
    // 读取超时时间
    private static int MAX_SO_TIME_OUT = 1000 * 5;
    //设置整个连接池最大连接数
    private static int MAX_TOTAL = 200;
    //设置单个路由默认连接数
    private static int PER_ROUTE_MAX_CONN = 50;
    //连接丢失后,重试次数
    private static int MAX_RETRY_COUNT = 2;
    //keepAlive 时间
    private static int KEEP_ALIVE_TIME = 1000 * 60;

    // 创建连接管理器
    private static PoolingHttpClientConnectionManager connManager = null;

    static {
        // jvm退出关闭连接池
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                if (connManager == null) {
                    return;
                }
                // 关闭连接池
                connManager.shutdown();
            }
        });
    }

    private HttpClientFactory() {
        createHttpClientConnectionManager();
    }

    private static class HttpClientFactoryInner {
        public static final HttpClientFactory httpClientFactory = new HttpClientFactory();
    }

    public static HttpClientFactory getInstance() {
        return HttpClientFactoryInner.httpClientFactory;
    }

    public HttpClient getHttpClient() {
        HttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connManager)
                .setRetryHandler(httpRequestRetry())
                .setDefaultRequestConfig(config())
                .evictExpiredConnections()
                .setKeepAliveStrategy(connectionKeepAliveStrategy())
                .build();

        return httpClient;
    }

    /**
     * 设置HttpClient连接池
     */
    private void createHttpClientConnectionManager() {
        try {
            // 创建SSLSocketFactory
            // 定义socket工厂类 指定协议（Http、Https）
            Registry registry = RegistryBuilder.create()
                    .register("https", SSLConnectionSocketFactory.getSocketFactory())
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .build();

            // 创建连接管理器
            connManager = new PoolingHttpClientConnectionManager(registry);
            connManager.setMaxTotal(MAX_TOTAL);//设置最大连接数
            connManager.setDefaultMaxPerRoute(PER_ROUTE_MAX_CONN);//设置每个路由默认连接数,单条链路最大连接数（一个ip+一个端口 是一个链路
        } catch (Exception e) {
            //throw new OpenSysException("创建httpClient(https)连接池异常", e);
            logger.error("创建httpClient(https)连接池异常", e);
        }
    }

    /**
     * 配置请求连接重试机制
     */
    private HttpRequestRetryHandler httpRequestRetry() {
        HttpRequestRetryHandler httpRequestRetryHandler = new HttpRequestRetryHandler() {
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                if (executionCount >= MAX_RETRY_COUNT) {// 如果已经重试MAX_EXECUT_COUNT次，就放弃
                    return false;
                }

                if (exception instanceof NoHttpResponseException) {// 如果服务器丢掉了连接，那么就重试
                    logger.error("httpclient 服务器连接丢失");
                    return true;
                }
                if (exception instanceof SSLHandshakeException) {// 不要重试SSL握手异常
                    logger.error("httpclient SSL握手异常");
                    return false;
                }
                if (exception instanceof InterruptedIOException) {// 超时
                    logger.error("httpclient 连接超时");
                    return false;
                }
                if (exception instanceof UnknownHostException) {// 目标服务器不可达
                    logger.error("httpclient 目标服务器不可达");
                    return false;
                }
                if (exception instanceof ConnectTimeoutException) {// 连接被拒绝
                    logger.error("httpclient 连接被拒绝");
                    return false;
                }
                if (exception instanceof SSLException) {// ssl握手异常
                    logger.error("httpclient SSLException");
                    return false;
                }

                return true;
            }
        };

        return httpRequestRetryHandler;
    }

    /**
     * 配置默认请求参数
     */
    private static RequestConfig config() {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(MAX_TIME_OUT)// 设置从连接池获取连接实例的超时
                .setConnectTimeout(MAX_TIME_OUT)// 设置连接超时
                .setSocketTimeout(MAX_SO_TIME_OUT)// 设置读取超时
                .build();

        return requestConfig;
    }

    private ConnectionKeepAliveStrategy connectionKeepAliveStrategy() {
        ConnectionKeepAliveStrategy keepAliveStrategy = new DefaultConnectionKeepAliveStrategy() {
            @Override
            public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
                long keepAlive = super.getKeepAliveDuration(response, context);
                if (keepAlive == -1) {
                    keepAlive = KEEP_ALIVE_TIME;
                }
                return keepAlive;
            }

        };

        return keepAliveStrategy;
    }
}
