ALTER TABLE quiz_questions
    DROP CHECK chk_quiz_questions_source_refs,
    ADD CONSTRAINT chk_quiz_questions_source_refs CHECK (
        (generation_type = 'HUMAN' AND source_refs_json IS NULL)
            OR
        (generation_type = 'AI'
            AND (
                source_refs_json IS NULL
                OR (
                    JSON_TYPE(source_refs_json) = 'ARRAY'
                    AND JSON_LENGTH(source_refs_json) > 0
                )
            ))
        );
