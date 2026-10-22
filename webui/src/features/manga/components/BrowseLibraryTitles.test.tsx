/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { BrowseLibraryTitles, useBrowseLibraryTitle } from '@/features/manga/components/BrowseLibraryTitles.tsx';

const { query } = vi.hoisted(() => ({ query: vi.fn() }));
vi.mock('@/lib/requests/RequestManager.ts', () => ({ requestManager: { useGetMangas: query } }));

const Card = ({ title }: { title: string }) => (
    <span>
        {title}: {String(useBrowseLibraryTitle(title))}
    </span>
);
afterEach(cleanup);

describe('Browse library title matching', () => {
    it('shares a batched lookup and matches case without marking alternate titles', () => {
        query.mockReturnValue({ data: { mangas: { nodes: [{ id: 99, title: 'One Piece', inLibrary: true }] } } });
        render(
            <BrowseLibraryTitles active titles={['ONE PIECE', 'Naruto', 'ONE PIECE']}>
                <Card title="ONE PIECE" />
                <Card title="Naruto" />
                <Card title="One Piece: Special" />
            </BrowseLibraryTitles>,
        );
        expect(screen.getByText('ONE PIECE: true')).toBeTruthy();
        expect(screen.getByText('Naruto: false')).toBeTruthy();
        expect(screen.getByText('One Piece: Special: false')).toBeTruthy();
        expect(query.mock.lastCall?.[1]).toEqual({ filter: { title: { inInsensitive: ['ONE PIECE', 'Naruto'] } } });
    });

    it('clears matches after removal and skips lookups outside browse', () => {
        query.mockReturnValue({ data: { mangas: { nodes: [{ id: 99, title: 'One Piece', inLibrary: false }] } } });
        const { rerender } = render(
            <BrowseLibraryTitles active titles={['One Piece']}>
                <Card title="One Piece" />
            </BrowseLibraryTitles>,
        );
        expect(screen.getByText('One Piece: false')).toBeTruthy();
        query.mockReturnValue({ data: { mangas: { nodes: [{ id: 99, title: 'One Piece', inLibrary: true }] } } });
        rerender(
            <BrowseLibraryTitles active={false} titles={['One Piece']}>
                <Card title="One Piece" />
            </BrowseLibraryTitles>,
        );
        expect(screen.getByText('One Piece: false')).toBeTruthy();
        expect(query.mock.lastCall?.[2].skip).toBe(true);
    });

    it('does not show a match before data arrives', () => {
        query.mockReturnValue({});
        render(
            <BrowseLibraryTitles active titles={['One Piece']}>
                <Card title="One Piece" />
            </BrowseLibraryTitles>,
        );
        expect(screen.getByText('One Piece: false')).toBeTruthy();
    });
});
