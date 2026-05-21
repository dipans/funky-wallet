package com.funkywallet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import java.util.concurrent.Executor;

@Configuration
public class AppConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    public WebClient signingWebClient(
            WebClient.Builder builder,
            @Value("${signing.coordinator.url}") String url) {
        return builder.baseUrl(url).build();
    }

    @Bean("chainWebClient")
    public WebClient chainWebClient(
            WebClient.Builder builder,
            @Value("${chain.adapter.evm.url}") String url) {
        return builder.baseUrl(url).build();
    }

    @Bean("solanaChainWebClient")
    public WebClient solanaChainWebClient(
            WebClient.Builder builder,
            @Value("${chain.adapter.solana.url}") String url) {
        return builder.baseUrl(url).build();
    }

    @Bean
    public Executor asyncExecutor() {
        SimpleAsyncTaskExecutor exec = new SimpleAsyncTaskExecutor();
        exec.setVirtualThreads(true); // Java 21 virtual threads
        return exec;
    }

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludeClientInfo(true);
        filter.setIncludeHeaders(false);
        filter.setIncludePayload(false); // never log request bodies — could contain mnemonics
        return filter;
    }
}
