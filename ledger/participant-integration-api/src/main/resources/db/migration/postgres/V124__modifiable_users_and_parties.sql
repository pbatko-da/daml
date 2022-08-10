--  Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
--  SPDX-License-Identifier: Apache-2.0



-- TODO pbatko: Consider storing annotations as single field (large strings) in participant_users and party_entries tables.
-- TODO pbatko: Consider implementing annotations size limit in db?
-- TODO pbatko: Use collations for annotations keys? COLLATE "C".

CREATE TABLE participant_user_annotations (
    user_internal_id           INTEGER             NOT NULL REFERENCES participant_users (internal_id) ON DELETE CASCADE,
    name                        TEXT                NOT NULL,
    val                      TEXT,
    updated_at                 BIGINT              NOT NULL,
    UNIQUE (user_internal_id, name)
);

CREATE TABLE participant_party_annotations (
    party_ledger_offset        VARCHAR             NOT NULL REFERENCES party_entries (ledger_offset) ON DELETE CASCADE,
    name                        TEXT                NOT NULL,
    val                      TEXT,
    updated_at                 BIGINT              NOT NULL,
    UNIQUE (party_ledger_offset, name)
);

ALTER TABLE participant_users ADD COLUMN is_deactivated BOOLEAN DEFAULT FALSE;
-- TODO pbatko: Figure out default values for resource versions
ALTER TABLE participant_users ADD COLUMN resource_version TEXT NOT NULL DEFAULT 'this-should-be-uuid-like';

-- TODO pbatko: Party's resource version in party_entries table (not null trouble) vs. a separate table (can be not null),
ALTER TABLE party_entries ADD COLUMN resource_version TEXT NOT NULL DEFAULT 'this-should-be-uuid-like';
