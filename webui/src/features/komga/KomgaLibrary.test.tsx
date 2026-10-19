/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { KomgaLibrary, useKomgaSummary } from '@/features/komga/KomgaLibrary.tsx';

const controls = vi.hoisted(() => ({
    query: vi.fn(),
    enabled: true,
    showCounter: true,
    apiUrl: 'http://first.invalid',
}));
vi.mock('@/lib/requests/RequestManager.ts', () => ({
    requestManager: { graphQLClient: { client: { query: controls.query } } },
}));
vi.mock('@/features/komga/KomgaSettings.tsx', () => ({
    useKomgaSettings: () => ({
        data: {
            komgaSettings: { enabled: controls.enabled, showCounter: controls.showCounter, apiUrl: controls.apiUrl },
        },
    }),
}));
const Consumer = () => <span>{useKomgaSummary(1)?.bookCount ?? 'unknown'}</span>;
beforeEach(() => {
    controls.enabled = true;
    controls.showCounter = true;
    controls.apiUrl = 'http://first.invalid';
    controls.query.mockReset();
    controls.query.mockImplementation(async ({ variables }: { variables?: { mangaIds: number[] } }) =>
        variables
            ? {
                  data: {
                      komgaLibrarySummaries: variables.mangaIds.map((mangaId) => ({
                          mangaId,
                          bookCount: 7,
                          code: 'READY',
                          provenance: 'MANUAL',
                          stale: false,
                      })),
                  },
              }
            : { data: { komgaScanStatus: { running: false } } },
    );
});
afterEach(cleanup);
it('deduplicates and batches a large grid without per-card inventory requests', async () => {
    render(
        <KomgaLibrary mangaIds={[1, ...Array.from({ length: 401 }, (_, i) => i + 1)]}>
            <Consumer />
        </KomgaLibrary>,
    );
    await screen.findByText('7');
    await waitFor(() => expect(controls.query.mock.calls.length).toBe(3));
    const batches = controls.query.mock.calls.filter(([options]) => options.variables);
    expect(batches.map(([options]) => options.variables.mangaIds.length)).toEqual([200, 200, 1]);
    expect(
        batches.every(([options]) =>
            options.query.definitions.some(
                (d: { name?: { value: string } }) => d.name?.value === 'KomgaLibrarySummaries',
            ),
        ),
    ).toBe(true);
});
it('does not load summaries when the counter and automatic linking are disabled', async () => {
    controls.showCounter = false;
    render(
        <KomgaLibrary mangaIds={[1]}>
            <Consumer />
        </KomgaLibrary>,
    );
    expect(screen.getByText('unknown')).toBeTruthy();
    expect(controls.query).not.toHaveBeenCalled();
});
it('immediately hides another destinations counts even when the replacement request fails', async () => {
    const view = render(
        <KomgaLibrary mangaIds={[1]}>
            <Consumer />
        </KomgaLibrary>,
    );
    await screen.findByText('7');
    controls.apiUrl = 'http://second.invalid';
    controls.query.mockRejectedValue(new Error('unavailable'));
    view.rerender(
        <KomgaLibrary mangaIds={[1]}>
            <Consumer />
        </KomgaLibrary>,
    );
    expect(screen.getByText('unknown')).toBeTruthy();
    expect(screen.queryByText('7')).toBeNull();
});
