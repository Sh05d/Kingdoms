package com.kingdom.Config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Value("${lemon.api.base.url}")
    private String lemonBaseUrl;

    @Value("${lemon.api.key}")
    private String lemonApiKey;

    @Value("${openai.api-key}")
    private String openAiKey;

    // Use the JVM's default (OS) DNS resolver instead of reactor-netty's async resolver. Netty's resolver fails to
    // resolve IPv6-primary Cloudflare hosts (e.g. api.lemonsqueezy.com -> "Failed to resolve ... [A(1), AAAA(28)]")
    // on Windows even though the OS resolves them fine — switching to the OS resolver makes outbound calls reliable.
    private ReactorClientHttpConnector osDnsConnector() {
        return new ReactorClientHttpConnector(
                HttpClient.create().resolver(DefaultAddressResolverGroup.INSTANCE));
    }

    @Bean
    @Qualifier("lemonWebClient")
    public WebClient lemonWebClient() {
        return WebClient.builder()
                .clientConnector(osDnsConnector())
                .baseUrl(lemonBaseUrl)
                .defaultHeader("Authorization", "Bearer " + lemonApiKey)
                .defaultHeader("Accept", "application/vnd.api+json")
                .defaultHeader("Content-Type", "application/vnd.api+json")
                .build();
    }

    @Bean
    @Qualifier("openAiWebClient")
    public WebClient openAiWebClient() {
        return WebClient.builder()
                .clientConnector(osDnsConnector())
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + openAiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}
