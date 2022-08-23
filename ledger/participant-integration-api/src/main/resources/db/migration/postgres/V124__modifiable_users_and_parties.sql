--  Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
--  SPDX-License-Identifier: Apache-2.0



-- TODO pbatko: Consider storing annotations as single field (large strings) in participant_users and party_entries tables.
-- TODO pbatko: Consider implementing annotations size limit in db?
-- TODO pbatko: Use collations for annotations keys? COLLATE "C".

CREATE TABLE participant_user_annotations (
    internal_id               INTEGER             NOT NULL REFERENCES participant_users (internal_id) ON DELETE CASCADE,
    name                      TEXT                NOT NULL,
    val                       TEXT,
    updated_at                BIGINT              NOT NULL,
    UNIQUE (internal_id, name)
);

CREATE TABLE participant_party_records (
    internal_id         SERIAL          PRIMARY KEY,
    party               VARCHAR(512)    NOT NULL UNIQUE COLLATE "C",
    resource_version    BIGINT          NOT NULL DEFAULT 0,
    created_at          BIGINT          NOT NULL
);


CREATE TABLE participant_party_annotations (
    internal_id               INTEGER             NOT NULL REFERENCES participant_party_records (internal_id) ON DELETE CASCADE,
    name                      TEXT                NOT NULL,
    val                       TEXT,
    updated_at                BIGINT              NOT NULL,
    UNIQUE (internal_id, name)
);

ALTER TABLE participant_users ADD COLUMN is_deactivated BOOLEAN DEFAULT FALSE;
ALTER TABLE participant_users ADD COLUMN resource_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE party_entries ADD COLUMN resource_version BIGINT NOT NULL DEFAULT 0;
