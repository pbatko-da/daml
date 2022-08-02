--  Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
--  SPDX-License-Identifier: Apache-2.0

-- TODO collations? COLLATE "C",

CREATE TABLE participant_user_annotations (
    user_internal_id           INTEGER             NOT NULL REFERENCES participant_users (internal_id) ON DELETE CASCADE,
    key                        TEXT,               NOT NULL,
    value                      TEXT,
    updated_at                 BIGINT              NOT NULL,
    UNIQUE (user_internal_id, key)
);

CREATE TABLE participant_party_annotations (
    party_ledger_offset        VARCHAR             NOT NULL REFERENCES party_entries (ledger_offset) ON DELETE CASCADE,
    key                        TEXT,               NOT NULL,
    value                      TEXT,
    updated_at                 BIGINT              NOT NULL,
    UNIQUE (party_ledger_offset, key)
);

ALTER TABLE participant_users ADD COLUMN is_deactivated BOOLEAN DEFAULT FALSE;
ALTER TABLE participant_users ADD COLUMN resource_version TEXT DEFAULT 'this-should-be-uuid-like';
ALTER TABLE party_entries ADD COLUMN resource_version TEXT DEFAULT 'this-should-be-uuid-like';
