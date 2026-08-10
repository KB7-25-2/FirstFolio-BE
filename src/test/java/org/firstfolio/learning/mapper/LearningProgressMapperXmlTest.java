package org.firstfolio.learning.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningProgressMapperXmlTest {

    @Test
    void parsesMapperAndRegistersProgressStatements() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mappers/learning/LearningProgressMapper.xml";

        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    input,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            ).parse();
        }

        assertTrue(configuration.hasMapper(LearningProgressMapper.class));
        assertTrue(configuration.hasStatement(
                LearningProgressMapper.class.getName()
                        + ".findByUserIdAndSubChapterId"
        ));
        assertTrue(configuration.hasStatement(
                LearningProgressMapper.class.getName() + ".insertIfAbsent"
        ));
        assertTrue(configuration.hasStatement(
                LearningProgressMapper.class.getName()
                        + ".findByUserIdAndSubChapterIdForUpdate"
        ));
        assertTrue(configuration.hasStatement(
                LearningProgressMapper.class.getName() + ".updateProgress"
        ));
        assertTrue(configuration.hasStatement(
                LearningProgressMapper.class.getName() + ".insertEvent"
        ));
    }
}
