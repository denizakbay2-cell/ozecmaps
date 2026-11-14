-- Businesses (shops, cafés, pharmacies, banks...) are places with a few extra details.
ALTER TABLE place
    ADD COLUMN business      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN kind          VARCHAR(60),
    ADD COLUMN opening_hours VARCHAR(255),
    ADD COLUMN phone         VARCHAR(60),
    ADD COLUMN website       VARCHAR(255),
    ADD CONSTRAINT ck_place_website_is_web_address CHECK (website IS NULL OR website ~* '^https?://');

-- the map asks for the businesses inside its view
CREATE INDEX ix_place_business_position ON place (lat, lon) WHERE business;
