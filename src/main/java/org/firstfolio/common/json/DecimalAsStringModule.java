package org.firstfolio.common.json;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.NumberSerializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * 금액·수량·단가를 JSON 문자열로 내보낸다 ({@code "30000000.00"}).
 *
 * <p>숫자로 내보내면 자릿수가 사라지고 지수 표기로 바뀌며
 * ({@code 30000000.00} → {@code 3.0E7}), 클라이언트에서 double로 읽히면서
 * 금액에 부동소수점 오차가 생긴다 (FUNC-036 예외/제한사항).</p>
 *
 * <p>비율처럼 숫자로 표현해야 하는 필드는 DTO에서 빠져나갈 수 있다:
 * <pre>
 * &#64;JsonFormat(shape = JsonFormat.Shape.NUMBER_FLOAT)
 * private BigDecimal ratio;
 * </pre>
 */
public final class DecimalAsStringModule extends SimpleModule {

    public DecimalAsStringModule() {
        addSerializer(BigDecimal.class, new DecimalAsStringSerializer());
    }

    private static final class DecimalAsStringSerializer
            extends JsonSerializer<BigDecimal>
            implements ContextualSerializer {

        @Override
        public void serialize(
                BigDecimal value,
                JsonGenerator generator,
                SerializerProvider provider
        ) throws IOException {
            generator.writeString(value.toPlainString());
        }

        /**
         * 필드에 {@code @JsonFormat(shape = NUMBER_*)}이 붙어 있으면 숫자로 내보낸다.
         */
        @Override
        public JsonSerializer<?> createContextual(
                SerializerProvider provider,
                BeanProperty property
        ) {
            if (property == null) {
                return this;
            }

            JsonFormat.Value format =
                    property.findPropertyFormat(provider.getConfig(), BigDecimal.class);

            return format.getShape().isNumeric()
                    ? new NumberSerializer(BigDecimal.class)
                    : this;
        }
    }
}
