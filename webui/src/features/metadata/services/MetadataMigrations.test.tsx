/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import '@/lib/dayjs/Setup.ts';
import '@/lib/koration/Setup.ts';
import { describe, expect, it, vi } from 'vitest';
import { METADATA_MIGRATIONS } from '@/features/metadata/Metadata.constants.ts';
import { applyMetadataMigrations } from '@/features/metadata/services/MetadataMigrations.ts';

vi.mock('@/features/metadata/services/MetadataUpdater.ts', () => ({ getMetadataUpdateFunction: () => vi.fn() }));

describe('saved search metadata migration', () => {
    it('preserves queries and values while converting grouped and ungrouped legacy filters', () => {
        const searches = {
            grouped: { query: 'test', filters: [{ position: 2, group: 0, state: 'value' }] },
            plain: { filters: [{ position: 3, state: false }] },
            current: { filters: [{ positions: [1, 4], state: 7 }] },
            queryOnly: { query: 'query only' },
        };
        const meta = {
            webUI_migration: String(METADATA_MIGRATIONS.length - 1),
            webUI_savedSearches: JSON.stringify(searches),
        };
        const migrated = applyMetadataMigrations('source', { id: 'fixture', meta }, () => {});
        expect(JSON.parse(migrated!.webUI_savedSearches)).toEqual({
            grouped: { query: 'test', filters: [{ positions: [0, 2], state: 'value' }] },
            plain: { filters: [{ positions: [3], state: false }] },
            current: searches.current,
            queryOnly: searches.queryOnly,
        });
        expect(meta.webUI_savedSearches).toBe(JSON.stringify(searches));
        const repeated = applyMetadataMigrations('source', { id: 'fixture', meta: migrated }, () => {});
        expect(repeated).toEqual(migrated);
    });
});
