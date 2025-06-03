package com.tang.springbootinit.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Knife4j 接口文档配置
 * https://doc.xiaominfo.com/knife4j/documentation/get_start.html
 *
 *  @author
 *  @from
 */
@Configuration
@Profile({"dev", "test"})
public class Knife4jConfig {

    @Bean
    public OpenAPI openApiInfo() {
        return new OpenAPI()
                .info(new Info()
                        .title("接口文档")
                        .description("tbi-backend")
                        .version("1.0"));
    }

    @Bean
    public GroupedOpenApi backendApi() {
        return GroupedOpenApi.builder()
                .group("default")
                .packagesToScan("com.tang.springbootinit.controller")
                .build();
    }
}
