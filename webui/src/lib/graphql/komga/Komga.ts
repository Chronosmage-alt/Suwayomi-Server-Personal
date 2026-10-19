/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import gql from 'graphql-tag';

const SETTINGS = gql`
    fragment KOMGA_SETTINGS on KomgaSettings {
        enabled
        apiUrl
        browserUrl
        credentialConfigured
        canManage
        autoLink
        showCounter
    }
`;
const INFO = gql`
    fragment KOMGA_INFO on KomgaMangaInfo {
        enabled
        linked
        provenance
        code
        message
        seriesId
        title
        openUrl
        fetchedAt
        stale
        inventoryComplete
        sourceEntries
        suwayomiDownloaded
        bookCount
        readBooks
        unreadBooks
        inProgressBooks
        lastProgressAt
        confirmedNumbers
        numberHints
        unmatchedSourceNumbers
        extraNumberHints
        confirmedButNotDownloaded
        downloadedButNotMatched
        missingCount
        ambiguousBooks
        unknownSourceEntries
        duplicateSourceEntries
        books {
            id
            title
            numberHint
            confirmedNumbers
            read
            page
            pages
            lastProgressAt
        }
    }
`;
export const KOMGA_SETTINGS = gql`
    ${SETTINGS}
    query KomgaSettings {
        komgaSettings {
            ...KOMGA_SETTINGS
        }
    }
`;
export const SAVE_KOMGA_SETTINGS = gql`
    ${SETTINGS}
    mutation SaveKomgaSettings(
        $enabled: Boolean!
        $apiUrl: String!
        $browserUrl: String!
        $autoLink: Boolean
        $showCounter: Boolean
    ) {
        setKomgaSettings(
            enabled: $enabled
            apiUrl: $apiUrl
            browserUrl: $browserUrl
            autoLink: $autoLink
            showCounter: $showCounter
        ) {
            ...KOMGA_SETTINGS
        }
    }
`;
export const TEST_KOMGA = gql`
    mutation TestKomga {
        testKomgaConnection {
            success
            code
            message
        }
    }
`;
export const KOMGA_SEARCH = gql`
    query KomgaSearch($query: String!, $page: Int!) {
        komgaSearch(query: $query, page: $page) {
            code
            message
            page
            hasNextPage
            items {
                id
                title
                alternateTitles
                bookCount
            }
        }
    }
`;
export const KOMGA_MANGA = gql`
    ${INFO}
    query KomgaManga($mangaId: Int!) {
        komgaManga(mangaId: $mangaId) {
            ...KOMGA_INFO
        }
    }
`;
export const LINK_KOMGA = gql`
    ${INFO}
    mutation LinkKomga($mangaId: Int!, $seriesId: String!) {
        linkKomgaSeries(mangaId: $mangaId, seriesId: $seriesId) {
            ...KOMGA_INFO
        }
    }
`;
export const UNLINK_KOMGA = gql`
    ${INFO}
    mutation UnlinkKomga($mangaId: Int!) {
        unlinkKomgaSeries(mangaId: $mangaId) {
            ...KOMGA_INFO
        }
    }
`;
export const REFRESH_KOMGA = gql`
    ${INFO}
    mutation RefreshKomga($mangaId: Int!) {
        refreshKomgaManga(mangaId: $mangaId) {
            ...KOMGA_INFO
        }
    }
`;

export const KOMGA_SUMMARIES = gql`
    query KomgaLibrarySummaries($mangaIds: [Int!]!) {
        komgaLibrarySummaries(mangaIds: $mangaIds) {
            mangaId
            provenance
            code
            bookCount
            fetchedAt
            stale
        }
    }
`;

const SCAN_STATUS = gql`
    fragment KOMGA_SCAN_STATUS on KomgaScanStatus {
        running
        code
        processed
        total
        automaticallyLinked
        alreadyLinked
        ambiguous
        optedOut
        unmatched
        indexedSeries
    }
`;
export const SCAN_KOMGA = gql`
    ${SCAN_STATUS}
    mutation ScanKomgaLibrary {
        scanKomgaLibrary {
            ...KOMGA_SCAN_STATUS
        }
    }
`;
export const KOMGA_SCAN_STATUS = gql`
    ${SCAN_STATUS}
    query KomgaScanStatus {
        komgaScanStatus {
            ...KOMGA_SCAN_STATUS
        }
    }
`;
