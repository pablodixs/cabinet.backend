DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fkdbp1gt36h2sj3rd6ps8wj6r8o'
    ) THEN
        ALTER TABLE album_tracks
            ADD CONSTRAINT FKdbp1gt36h2sj3rd6ps8wj6r8o
            FOREIGN KEY (album_media_id) REFERENCES media(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fkel8dxag5l39hgbe9lfsk05cmd'
    ) THEN
        ALTER TABLE media_genres
            ADD CONSTRAINT FKel8dxag5l39hgbe9lfsk05cmd
            FOREIGN KEY (media_id) REFERENCES media(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fke8ceqthv8hlm8uo2aag7q11e0'
    ) THEN
        ALTER TABLE reviews
            ADD CONSTRAINT FKe8ceqthv8hlm8uo2aag7q11e0
            FOREIGN KEY (media_id) REFERENCES media(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fkqitdcph2ilwddjql0vcx872tm'
    ) THEN
        ALTER TABLE series_details
            ADD CONSTRAINT FKqitdcph2ilwddjql0vcx872tm
            FOREIGN KEY (media_id) REFERENCES media(id) NOT VALID;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fke0m0wp1cueqn878u0m3p05356'
    ) THEN
        ALTER TABLE series_seasons
            ADD CONSTRAINT FKe0m0wp1cueqn878u0m3p05356
            FOREIGN KEY (series_media_id) REFERENCES media(id) NOT VALID;
    END IF;
END
$$;
