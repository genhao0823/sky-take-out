package com.sky.config;

import com.sky.properties.AliOssProperties;
import com.sky.utils.AliOssUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
* 配置类用于创建AliOssUtil对象
*@Configuration
作用：告诉 Spring 这是一个配置类。
意义：Spring 容器在启动时会扫描这个类，并执行其中带有 @Bean 的方法，将返回的对象注册进 Spring IOC 容器中。
@Slf4j
作用：Lombok 提供的注解，自动在类中生成一个名为 log 的日志对象。
意义：让你可以在代码中直接使用 log.info() 或 log.error() 打印运行日志。
@Bean
作用：标注在方法上，告诉 Spring：这个方法的返回值是一个 Bean（对象），请把它放进 IOC 容器管理。
意义：默认情况下，这个 Bean 的名字就是方法名 aliOssUtil。
@ConditionalOnMissingBean
作用：条件装配。意思是“当容器中没有这个类型的 Bean 时，才执行当前方法”。
意义：防止重复创建对象。如果用户在其他地方手动配置了一个 AliOssUtil，那么这个默认配置就不会生效，增加了代码的灵活性。
* */
@Configuration
@Slf4j
public class OssConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public AliOssUtil aliOssUtil(AliOssProperties aliOssProperties) {
        log.info("开始创建AliOssUtil对象{}", aliOssProperties);
        return new AliOssUtil(aliOssProperties.getEndpoint(), aliOssProperties.getAccessKeyId(),
                aliOssProperties.getAccessKeySecret(), aliOssProperties.getBucketName());
    }
}
