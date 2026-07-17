DO $$
BEGIN
    IF to_regclass('media_reports') IS NOT NULL THEN
        ALTER TABLE media_reports
            DROP CONSTRAINT IF EXISTS media_reports_suggested_relation_type_check;

        ALTER TABLE media_reports
            ADD CONSTRAINT media_reports_suggested_relation_type_check
            CHECK (
                suggested_relation_type IS NULL
                OR suggested_relation_type IN (
                    'ADAPTATION_OF',
                    'ADAPTED_AS',
                    'SOUNDTRACK',
                    'SOUNDTRACK_OF',
                    'RE_RECORDING_OF',
                    'RE_RECORDED_AS'
                )
            );
    END IF;

    IF to_regclass('media_relations') IS NOT NULL THEN
        ALTER TABLE media_relations
            DROP CONSTRAINT IF EXISTS media_relations_relation_type_check;

        ALTER TABLE media_relations
            ADD CONSTRAINT media_relations_relation_type_check
            CHECK (
                relation_type IN (
                    'ADAPTATION_OF',
                    'ADAPTED_AS',
                    'SOUNDTRACK',
                    'SOUNDTRACK_OF',
                    'RE_RECORDING_OF',
                    'RE_RECORDED_AS'
                )
            );
    END IF;
END
$$;
