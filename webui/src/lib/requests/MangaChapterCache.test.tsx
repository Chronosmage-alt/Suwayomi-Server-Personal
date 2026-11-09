/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { InMemoryCache, gql } from '@apollo/client/core';
import { describe, expect, it, vi } from 'vitest';
import { evictMangaChapterLists } from '@/lib/requests/MangaChapterCache.ts';

const listQuery = gql`
    query Chapters($condition: ChapterConditionInput) {
        chapters(condition: $condition) {
            nodes {
                id
                name
            }
        }
    }
`;

describe('manga chapter cache invalidation', () => {
    it('evicts only affected chapter lists in one pass without extracting the entity cache', () => {
        const cache = new InMemoryCache({
            typePolicies: { Query: { fields: { chapters: { keyArgs: ['condition'] } } } },
        });
        for (const mangaId of [1, 11, 2]) {
            cache.writeQuery({
                query: listQuery,
                variables: { condition: { mangaId } },
                data: { chapters: { nodes: [{ __typename: 'ChapterType', id: mangaId, name: 'Chapter' }] } },
            });
        }
        // Global Updates/History pages must survive invalidation of manga-specific lists.
        cache.writeQuery({ query: listQuery, data: { chapters: { nodes: [] } } });
        const extract = vi.spyOn(cache, 'extract');
        const modify = vi.spyOn(cache, 'modify');
        evictMangaChapterLists(cache, [1, 1, 2]);
        expect(modify).toHaveBeenCalledOnce();
        expect(extract).not.toHaveBeenCalled();
        expect(cache.readQuery({ query: listQuery, variables: { condition: { mangaId: 1 } } })).toBeNull();
        expect(cache.readQuery({ query: listQuery, variables: { condition: { mangaId: 2 } } })).toBeNull();
        expect(cache.readQuery({ query: listQuery, variables: { condition: { mangaId: 11 } } })).not.toBeNull();
        expect(cache.readQuery({ query: listQuery })).not.toBeNull();
        expect(
            cache.readFragment({
                id: 'ChapterType:1',
                fragment: gql`
                    fragment ChapterName on ChapterType {
                        name
                    }
                `,
            }),
        ).toEqual({ __typename: 'ChapterType', name: 'Chapter' });
    });

    it('does no cache work for empty updater events', () => {
        const cache = new InMemoryCache();
        const modify = vi.spyOn(cache, 'modify');
        evictMangaChapterLists(cache, []);
        expect(modify).not.toHaveBeenCalled();
    });
});
