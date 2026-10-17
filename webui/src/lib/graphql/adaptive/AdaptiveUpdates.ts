/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import gql from 'graphql-tag';

const ADAPTIVE_SETTINGS_FIELDS = gql`
    fragment ADAPTIVE_SETTINGS_FIELDS on AdaptiveUpdateSettings {
        enabled
        canManage
        maxBackoffDays
        sources {
            sourceId
            providerPackage
            name
            enabled
            installed
        }
    }
`;

const ADAPTIVE_MANGA_FIELDS = gql`
    fragment ADAPTIVE_MANGA_FIELDS on AdaptiveMangaSchedule {
        mangaId
        mode
        canManage
        effective
        reason
        intervalDays
        provisionalIntervalDays
        historicalConfirmations
        observations
        emptyChecks
        lastCheckAt
        lastDiscoveryAt
        lastReleaseAt
        usesPublicationDate
        nextEligibleAt
        nextAutomaticCheckAt
    }
`;

export const GET_ADAPTIVE_SETTINGS = gql`
    ${ADAPTIVE_SETTINGS_FIELDS}
    query GET_ADAPTIVE_SETTINGS {
        adaptiveUpdateSettings {
            ...ADAPTIVE_SETTINGS_FIELDS
        }
    }
`;
export const SET_ADAPTIVE_ENABLED = gql`
    ${ADAPTIVE_SETTINGS_FIELDS}
    mutation SET_ADAPTIVE_ENABLED($enabled: Boolean!) {
        setAdaptiveUpdatesEnabled(enabled: $enabled) {
            ...ADAPTIVE_SETTINGS_FIELDS
        }
    }
`;
export const SET_ADAPTIVE_SOURCE = gql`
    ${ADAPTIVE_SETTINGS_FIELDS}
    mutation SET_ADAPTIVE_SOURCE($sourceId: LongString!, $enabled: Boolean!) {
        setAdaptiveSource(sourceId: $sourceId, enabled: $enabled) {
            ...ADAPTIVE_SETTINGS_FIELDS
        }
    }
`;
export const GET_ADAPTIVE_MANGA = gql`
    ${ADAPTIVE_MANGA_FIELDS}
    query GET_ADAPTIVE_MANGA($mangaId: Int!) {
        adaptiveMangaSchedule(mangaId: $mangaId) {
            ...ADAPTIVE_MANGA_FIELDS
        }
    }
`;
export const SET_ADAPTIVE_MANGA_MODE = gql`
    ${ADAPTIVE_MANGA_FIELDS}
    mutation SET_ADAPTIVE_MANGA_MODE($mangaId: Int!, $mode: AdaptiveUpdateMode!) {
        setAdaptiveMangaMode(mangaId: $mangaId, mode: $mode) {
            ...ADAPTIVE_MANGA_FIELDS
        }
    }
`;
export const RESET_ADAPTIVE_MANGA = gql`
    ${ADAPTIVE_MANGA_FIELDS}
    mutation RESET_ADAPTIVE_MANGA($mangaId: Int!) {
        resetAdaptiveMangaHistory(mangaId: $mangaId) {
            ...ADAPTIVE_MANGA_FIELDS
        }
    }
`;
