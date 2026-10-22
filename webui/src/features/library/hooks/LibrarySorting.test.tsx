/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import '@/lib/dayjs/Setup.ts';
import '@/lib/koration/Setup.ts';
import { renderHook } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useSortedMangas } from '@/features/library/hooks/useGetVisibleLibraryMangas.ts';
import type { GetMangasLibraryQuery } from '@/lib/graphql/generated/graphql.ts';

vi.mock('@/lib/requests/RequestManager.ts', () => ({ requestManager: {} }));

type Manga = GetMangasLibraryQuery['mangas']['nodes'][number];
const manga = (id: number, unreadCount: number) => ({ id, title: `Manga ${id}`, unreadCount }) as Manga;

describe('library sorting during live updates', () => {
    it('reorders an existing category when chapter counts change', () => {
        const initial = [manga(1, 2), manga(2, 5)];
        const { result, rerender } = renderHook(
            ({ items }) => useSortedMangas(901, items, { sortBy: 'unreadChapters', sortDesc: false }),
            { initialProps: { items: initial } },
        );
        expect(result.current.map(({ id }) => id)).toEqual([1, 2]);
        const updated = [manga(1, 10), manga(2, 5)];
        rerender({ items: updated });
        expect(result.current.map(({ id }) => id)).toEqual([2, 1]);
        expect(result.current[1]).toBe(updated[0]);
    });

    it('keeps random order while replacing refreshed card data', () => {
        const random = vi.spyOn(Math, 'random').mockReturnValue(0.7);
        const { result, rerender } = renderHook(
            ({ items }) => useSortedMangas(902, items, { sortBy: 'random', sortDesc: false }),
            { initialProps: { items: [manga(1, 2), manga(2, 5), manga(3, 1)] } },
        );
        const order = result.current.map(({ id }) => id);
        const updated = [manga(1, 10), manga(2, 8), manga(3, 4)];
        rerender({ items: updated });
        expect(result.current.map(({ id }) => id)).toEqual(order);
        expect(result.current.every((item) => updated.includes(item))).toBe(true);
        random.mockRestore();
    });
});
