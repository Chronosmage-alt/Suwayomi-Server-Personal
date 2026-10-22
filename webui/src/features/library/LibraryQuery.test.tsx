/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { describe, expect, it } from 'vitest';
import { getDirectiveValues, GraphQLIncludeDirective, visit } from 'graphql';
import { GET_MANGAS_LIBRARY } from '@/lib/graphql/manga/MangaQuery.ts';
import { getLibraryQueryVariables } from '@/features/library/LibraryQuery.ts';
import type { LibrarySortMode } from '@/features/library/Library.types.ts';

const requestedFields = (sortBy?: LibrarySortMode, showContinueReading = true) => {
    const fields = new Set<string>();
    visit(GET_MANGAS_LIBRARY, {
        Field(node) {
            if (
                getDirectiveValues(GraphQLIncludeDirective, node, {
                    sources: {},
                    coerced: getLibraryQueryVariables(sortBy, showContinueReading),
                })?.if === false
            ) {
                return false;
            }
            fields.add(node.name.value);
            return undefined;
        },
    });
    return fields;
};

describe('library category query cost', () => {
    it('avoids finding unread chapter targets when continue-reading buttons are disabled', () => {
        expect(requestedFields(undefined, false).has('firstUnreadChapter')).toBe(false);
        expect(requestedFields(undefined, true).has('firstUnreadChapter')).toBe(true);
    });
    it('keeps badges, continue reading and migration data without unused chapter lookups', () => {
        const fields = requestedFields();
        for (const field of [
            'unreadCount',
            'downloadCount',
            'bookmarkCount',
            'hasDuplicateChapters',
            'firstUnreadChapter',
            'highestNumberedChapter',
            'trackRecords',
        ]) {
            expect(fields.has(field)).toBe(true);
        }
        for (const field of ['lastReadChapter', 'latestReadChapter', 'latestFetchedChapter', 'latestUploadedChapter']) {
            expect(fields.has(field)).toBe(false);
        }
    });

    it.each([
        ['lastRead', 'lastReadChapter'],
        ['latestFetchedChapter', 'latestFetchedChapter'],
        ['latestUploadedChapter', 'latestUploadedChapter'],
    ] as const)('loads the chapter data needed for %s sorting', (sortBy, expected) => {
        const fields = requestedFields(sortBy);
        expect(fields.has(expected)).toBe(true);
        expect(fields.has('latestReadChapter')).toBe(false);
        expect(
            ['lastReadChapter', 'latestFetchedChapter', 'latestUploadedChapter'].filter((field) => fields.has(field)),
        ).toEqual([expected]);
    });
});
