package org.firstfolio.common.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiDtoNamingContractTest {

    private static final Pattern SNAKE_CASE = Pattern.compile(
            "^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$"
    );
    private static final String REQUEST_DTO_PATTERN =
            "classpath*:org/firstfolio/**/dto/request/*.class";
    private static final String RESPONSE_DTO_PATTERN =
            "classpath*:org/firstfolio/**/dto/response/*.class";

    private final ObjectMapper objectMapper = ApiObjectMapperFactory.create();

    @Test
    void allOwnedApiRequestPropertiesUseSnakeCase() throws Exception {
        assertDtoPropertiesUseSnakeCase(
                REQUEST_DTO_PATTERN,
                description -> description.findProperties().stream()
                        .filter(BeanPropertyDefinition::couldDeserialize)
                        .toList(),
                objectMapper.getDeserializationConfig()::introspect
        );
    }

    @Test
    void allOwnedApiResponsePropertiesUseSnakeCase() throws Exception {
        assertDtoPropertiesUseSnakeCase(
                RESPONSE_DTO_PATTERN,
                description -> description.findProperties().stream()
                        .filter(BeanPropertyDefinition::couldSerialize)
                        .toList(),
                objectMapper.getSerializationConfig()::introspect
        );
    }

    private void assertDtoPropertiesUseSnakeCase(
            String resourcePattern,
            Function<BeanDescription, List<BeanPropertyDefinition>> properties,
            Function<JavaType, BeanDescription> introspector
    ) throws Exception {
        Set<String> violations = new TreeSet<>();
        List<Class<?>> dtoClasses = findDtoClasses(resourcePattern);

        assertFalse(dtoClasses.isEmpty(), "검사할 API DTO를 찾지 못했습니다.");

        for (Class<?> dtoClass : dtoClasses) {
            JavaType type = objectMapper.getTypeFactory().constructType(dtoClass);
            for (BeanPropertyDefinition property : properties.apply(introspector.apply(type))) {
                if (!SNAKE_CASE.matcher(property.getName()).matches()) {
                    violations.add(dtoClass.getName() + "." + property.getName());
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "snake_case가 아닌 FirstFolio API 필드: " + violations
        );
    }

    private List<Class<?>> findDtoClasses(String resourcePattern) throws Exception {
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver();
        MetadataReaderFactory metadataReaderFactory =
                new CachingMetadataReaderFactory(resolver);

        return Arrays.stream(resolver.getResources(resourcePattern))
                .map(resource -> className(resource, metadataReaderFactory))
                .distinct()
                .sorted()
                .map(this::loadClass)
                .toList();
    }

    private String className(
            Resource resource,
            MetadataReaderFactory metadataReaderFactory
    ) {
        try {
            return metadataReaderFactory.getMetadataReader(resource)
                    .getClassMetadata()
                    .getClassName();
        } catch (Exception exception) {
            throw new IllegalStateException("API DTO 메타데이터를 읽을 수 없습니다.", exception);
        }
    }

    private Class<?> loadClass(String className) {
        try {
            return ClassUtils.forName(className, getClass().getClassLoader());
        } catch (Exception exception) {
            throw new IllegalStateException("API DTO 클래스를 불러올 수 없습니다: " + className, exception);
        }
    }
}
