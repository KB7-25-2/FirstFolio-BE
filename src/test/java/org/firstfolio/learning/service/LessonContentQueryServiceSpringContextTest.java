package org.firstfolio.learning.service;

import org.firstfolio.content.mapper.ContentVersionMapper;
import org.firstfolio.content.service.StaticContentStorage;
import org.firstfolio.curriculum.mapper.SubChapterMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class LessonContentQueryServiceSpringContextTest {

    @Test
    void createsServiceUsingItsDependencyInjectionConstructor() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(TestConfig.class)) {
            assertNotNull(context.getBean(LessonContentQueryService.class));
        }
    }

    @Configuration
    @Import(LessonContentQueryService.class)
    static class TestConfig {

        @Bean
        ContentVersionMapper contentVersionMapper() {
            return mock(ContentVersionMapper.class);
        }

        @Bean
        SubChapterMapper subChapterMapper() {
            return mock(SubChapterMapper.class);
        }

        @Bean
        StaticContentStorage staticContentStorage() {
            return mock(StaticContentStorage.class);
        }
    }
}
