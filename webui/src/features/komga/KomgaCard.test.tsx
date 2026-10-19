/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { I18nProvider } from '@lingui/react';
import { i18n } from '@lingui/core';
import { KomgaCard } from '@/features/komga/KomgaCard.tsx';

const controls = vi.hoisted(() => ({
    enabled: true,
    info: {} as Record<string, unknown>,
    write: vi.fn(),
    link: vi.fn(),
    unlink: vi.fn(),
    refresh: vi.fn(),
    query: vi.fn(),
}));
vi.mock('@/lib/requests/RequestManager.ts', () => ({
    requestManager: { graphQLClient: { client: { writeQuery: controls.write } } },
}));
vi.mock('@/features/komga/KomgaSettings.tsx', () => ({
    useKomgaSettings: () => ({ data: { komgaSettings: { enabled: controls.enabled } } }),
}));
vi.mock('@apollo/client/react', () => ({
    useQuery: (doc: { definitions: { name?: { value: string } }[] }, options: unknown) => {
        controls.query(options);
        return doc.definitions.some((d) => d.name?.value === 'KomgaManga')
            ? { data: { komgaManga: controls.info } }
            : {
                  data: {
                      komgaSearch: {
                          code: 'READY',
                          message: '',
                          items: [{ id: 's', title: 'Suggested series', alternateTitles: [], bookCount: 120 }],
                          hasNextPage: false,
                      },
                  },
              };
    },
    useMutation: (doc: { definitions: { name?: { value: string } }[] }) => {
        if (doc.definitions.some((d) => d.name?.value === 'LinkKomga')) {
            return [controls.link, { loading: false }];
        }
        if (doc.definitions.some((d) => d.name?.value === 'UnlinkKomga')) {
            return [controls.unlink, { loading: false }];
        }
        return [controls.refresh, { loading: false }];
    },
}));
beforeEach(() => {
    vi.clearAllMocks();
    controls.enabled = true;
    controls.info = {
        enabled: true,
        linked: true,
        code: 'READY',
        message: 'Numbering uncertain',
        title: 'Fixture',
        seriesId: 's',
        openUrl: 'http://nas:8080/series/s',
        fetchedAt: '1000',
        stale: false,
        inventoryComplete: true,
        sourceEntries: 120,
        suwayomiDownloaded: 70,
        bookCount: 120,
        readBooks: 90,
        unreadBooks: 30,
        inProgressBooks: 0,
        lastProgressAt: null,
        confirmedNumbers: ['1'],
        numberHints: ['2'],
        unmatchedSourceNumbers: ['2'],
        extraNumberHints: [],
        confirmedButNotDownloaded: ['1'],
        downloadedButNotMatched: [],
        missingCount: null,
        ambiguousBooks: 119,
        unknownSourceEntries: 0,
        duplicateSourceEntries: 0,
        books: [],
    };
    controls.write.mockImplementation(({ data }: { data: { komgaManga: Record<string, unknown> } }) => {
        controls.info = data.komgaManga;
    });
    controls.link.mockResolvedValue({ data: { linkKomgaSeries: controls.info } });
    controls.unlink.mockResolvedValue({
        data: { unlinkKomgaSeries: { ...controls.info, linked: false, code: 'UNLINKED' } },
    });
    controls.refresh.mockResolvedValue({ data: { refreshKomgaManga: controls.info } });
    i18n.load('en', {});
    i18n.activate('en');
});
afterEach(cleanup);
const show = () =>
    render(
        <I18nProvider i18n={i18n}>
            <KomgaCard mangaId={42} title="Fixture" />
        </I18nProvider>,
    );

const books = (prefix: string, length: number) =>
    Array.from({ length }, (_, index) => ({
        id: `${prefix}-${index}`,
        title: `${prefix} book ${index}`,
        read: false,
    }));

const secondBookPage = () => {
    fireEvent.click(screen.getByRole('button', { name: 'Inventory comparison and book progress' }));
    fireEvent.click(screen.getAllByRole('button', { name: 'Next' })[0]);
    expect(screen.queryByText('Original book 0')).toBeNull();
    expect(screen.getByText('Original book 50')).toBeTruthy();
};

describe('read-only Komga manga card', () => {
    it('resets inventory pagination and search state when switching manga', () => {
        controls.info.books = books('Original', 60);
        const view = show();
        secondBookPage();
        fireEvent.click(screen.getByRole('button', { name: 'Correct Komga link' }));
        fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Old search' } });
        controls.info = { ...controls.info, seriesId: 'other', books: books('New', 2) };
        view.rerender(
            <I18nProvider i18n={i18n}>
                <KomgaCard mangaId={43} title="New manga" />
            </I18nProvider>,
        );
        expect(screen.queryByRole('dialog')).toBeNull();
        fireEvent.click(screen.getByRole('button', { name: 'Inventory comparison and book progress' }));
        expect(screen.getByText('New book 0')).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: 'Correct Komga link' }));
        expect((screen.getByRole('textbox') as HTMLInputElement).value).toBe('New manga');
    });

    it('returns to the first book page after confirming a different series', async () => {
        controls.info.books = books('Original', 60);
        controls.info.seriesId = 'old';
        controls.link.mockResolvedValue({
            data: { linkKomgaSeries: { ...controls.info, seriesId: 's', books: books('Linked', 2) } },
        });
        show();
        secondBookPage();
        fireEvent.click(screen.getByRole('button', { name: 'Correct Komga link' }));
        fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Confirm link' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
        expect(screen.getByText('Linked book 0')).toBeTruthy();
        expect(screen.getAllByRole('button', { name: 'Previous' })[0].hasAttribute('disabled')).toBe(true);
    });
    it('skips inventory when disabled', () => {
        controls.enabled = false;
        const view = show();
        expect(view.container.textContent).toBe('');
        expect(controls.query.mock.calls[0][0].skip).toBe(true);
    });
    it('keeps book progress separate from uncertain chapter totals', () => {
        show();
        expect(screen.getByText(/Suwayomi downloaded entries: 70 \/ 120/)).toBeTruthy();
        expect(screen.getByText(/Read: 90, unread: 30/)).toBeTruthy();
        expect(screen.getByText(/Missing: uncertain/)).toBeTruthy();
        expect(screen.getByRole('link', { name: 'Open in Komga' }).getAttribute('href')).toBe(
            'http://nas:8080/series/s',
        );
    });
    it('requires manual confirmation of a search suggestion', async () => {
        show();
        fireEvent.click(screen.getByRole('button', { name: 'Correct Komga link' }));
        expect(screen.getByText('Suggested series')).toBeTruthy();
        expect(controls.link).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('button', { name: 'Confirm link' }));
        await waitFor(() => expect(controls.link).toHaveBeenCalledWith({ variables: { mangaId: 42, seriesId: 's' } }));
    });
    it('refreshes or unlinks only through dedicated Komga operations', async () => {
        show();
        fireEvent.click(screen.getByRole('button', { name: 'Refresh Komga' }));
        await waitFor(() => expect(controls.refresh).toHaveBeenCalledWith({ variables: { mangaId: 42 } }));
        fireEvent.click(screen.getByRole('button', { name: 'Unlink' }));
        await waitFor(() => expect(controls.unlink).toHaveBeenCalledWith({ variables: { mangaId: 42 } }));
    });
    it('labels stale data and preserves connection failures', () => {
        controls.info.stale = true;
        controls.info.code = 'STALE';
        controls.info.message = 'Komga unavailable; cached inventory';
        show();
        expect(screen.getByText('Komga unavailable; cached inventory')).toBeTruthy();
        expect(screen.getByText(/cached; Komga is unavailable/)).toBeTruthy();
    });
});
