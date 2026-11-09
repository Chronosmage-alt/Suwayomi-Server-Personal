/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import type { ApolloCache } from '@apollo/client/core';

/** Invalidate affected manga lists without copying every cached chapter and manga. */
export const evictMangaChapterLists = (cache: ApolloCache, mangaIds: readonly number[]): void => {
    if (!mangaIds.length) {
        return;
    }
    const affected = new Set(mangaIds);
    cache.modify({
        id: 'ROOT_QUERY',
        fields: {
            chapters(existing, { storeFieldName, DELETE }) {
                // GraphQLClient's chapters keyArgs retain condition/filter/order, excluding pagination.
                if (!storeFieldName.startsWith('chapters:')) {
                    return existing;
                }
                const args = JSON.parse(storeFieldName.slice('chapters:'.length));
                return affected.has(args.condition?.mangaId) ? DELETE : existing;
            },
        },
    });
};
