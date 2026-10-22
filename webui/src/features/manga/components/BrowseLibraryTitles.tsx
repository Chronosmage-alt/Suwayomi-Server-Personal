/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { createContext, useContext, useMemo, type ReactNode } from 'react';
import gql from 'graphql-tag';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import type { GetMangasBaseQueryVariables } from '@/lib/graphql/generated/graphql.ts';

export const BROWSE_LIBRARY_TITLES = gql`
    query BrowseLibraryTitles($filter: MangaFilterInput) {
        mangas(condition: { inLibrary: true }, filter: $filter) {
            nodes {
                id
                title
                inLibrary
            }
        }
    }
`;

type LibraryTitle = { id: number; title: string; inLibrary: boolean };
const Context = createContext<ReadonlySet<string>>(new Set());
export const useBrowseLibraryTitle = (title: string) => useContext(Context).has(title.toUpperCase());

/** One lightweight, batched lookup per grid, shared by all cards. */
export const BrowseLibraryTitles = ({
    children,
    titles,
    active,
}: {
    children: ReactNode;
    titles: string[];
    active: boolean;
}) => {
    const { data } = requestManager.useGetMangas<{ mangas: { nodes: LibraryTitle[] } }, GetMangasBaseQueryVariables>(
        BROWSE_LIBRARY_TITLES,
        { filter: { title: { inInsensitive: [...new Set(titles)] } } },
        { skip: !active || !titles.length, fetchPolicy: 'cache-and-network' },
    );
    const libraryTitles = useMemo(
        () =>
            new Set(
                active
                    ? data?.mangas.nodes.filter((manga) => manga.inLibrary).map((manga) => manga.title.toUpperCase())
                    : [],
            ),
        [active, data],
    );
    return <Context.Provider value={libraryTitles}>{children}</Context.Provider>;
};
