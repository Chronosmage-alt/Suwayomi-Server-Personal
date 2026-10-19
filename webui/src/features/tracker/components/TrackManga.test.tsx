/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { I18nProvider } from '@lingui/react';
import { i18n } from '@lingui/core';
import { TrackManga } from '@/features/tracker/components/TrackManga.tsx';

const controls = vi.hoisted(() => ({ refresh: vi.fn() }));
vi.mock('@/lib/requests/RequestManager.ts', () => ({
    requestManager: {
        useGetTrackerList: () => ({
            data: {
                trackers: {
                    nodes: [
                        { id: 1, name: 'AniList', isLoggedIn: true, isTokenExpired: false },
                        { id: 2, name: 'MyAnimeList', isLoggedIn: true, isTokenExpired: false },
                    ],
                },
            },
        }),
        useGetManga: () => ({
            data: {
                manga: {
                    trackRecords: {
                        nodes: [
                            { id: 101, trackerId: 1 },
                            { id: 102, trackerId: 2 },
                        ],
                    },
                },
            },
        }),
        fetchTrackBind: (id: number) => {
            controls.refresh(id);
            return { response: Promise.resolve() };
        },
    },
}));
vi.mock('@/features/tracker/components/cards/TrackerCard.tsx', () => ({
    TrackerMode: { SEARCH: 'SEARCH', INFO: 'INFO', UNTRACKED: 'UNTRACKED' },
    TrackerCard: ({ tracker }: { tracker: { name: string } }) => <p>{tracker.name}</p>,
}));
vi.mock('@/base/utils/Toast.ts', () => ({ makeToast: vi.fn() }));
afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

describe('existing tracker providers', () => {
    it('keeps linked AniList and MAL visible and refreshes each only once', () => {
        i18n.load('en', {});
        i18n.activate('en');
        const ui = (
            <I18nProvider i18n={i18n}>
                <MemoryRouter>
                    <TrackManga manga={{ id: 42, title: 'Fixture' }} />
                </MemoryRouter>
            </I18nProvider>
        );
        const view = render(ui);
        expect(screen.getByText('AniList')).toBeTruthy();
        expect(screen.getByText('MyAnimeList')).toBeTruthy();
        expect(controls.refresh.mock.calls).toEqual([[101], [102]]);
        view.rerender(ui);
        expect(controls.refresh).toHaveBeenCalledTimes(2);
    });
});
