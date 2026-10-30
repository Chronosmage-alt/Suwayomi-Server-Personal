/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { i18n } from '@lingui/core';
import { I18nProvider } from '@lingui/react';
import { MemoryRouter } from 'react-router-dom';
import { ChapterCard } from '@/features/chapter/components/cards/ChapterCard.tsx';

vi.mock('@/features/chapter/services/Chapters.ts', () => ({
    Chapters: { getReaderOpenChapterLocationState: () => ({}) },
}));
vi.mock('@/features/reader/services/ReaderService.ts', () => ({ ReaderService: { openReader: vi.fn() } }));
vi.mock('@/features/chapter/components/actions/ChapterActionMenuItems.tsx', () => ({
    ChapterActionMenuItems: () => null,
}));
vi.mock('@/base/components/downloads/DownloadStateIndicatorCircular.tsx', () => ({
    DownloadStateIndicatorCircular: () => null,
}));
vi.mock('@/base/hooks/usePress.ts', () => ({ usePress: () => () => ({}) }));
vi.mock('@/base/utils/DateHelper.ts', () => ({ getDateString: () => 'Today' }));
vi.mock('@/base/utils/MediaQuery.tsx', () => ({ MediaQuery: { preventMobileContextMenuSx: () => ({}) } }));

i18n.load('en', {});
i18n.activate('en');
afterEach(cleanup);

describe('chapter list reading progress', () => {
    it.each([
        { lastPageRead: 4, pageCount: 20, isRead: false, expected: 'Today — Page: 5/20 — Downloaded' },
        { lastPageRead: 4, pageCount: 0, isRead: false, expected: 'Today — Page: 5 — Downloaded' },
        { lastPageRead: 40, pageCount: 20, isRead: false, expected: 'Today — Page: 20/20 — Downloaded' },
        { lastPageRead: 0, pageCount: 20, isRead: false, expected: 'Today — Downloaded' },
        { lastPageRead: 19, pageCount: 20, isRead: true, expected: 'Today — Downloaded' },
    ])('renders $expected', ({ expected, ...progress }) => {
        render(
            <I18nProvider i18n={i18n}>
                <MemoryRouter>
                    <ChapterCard
                        chapter={{
                            id: 1,
                            mangaId: 2,
                            name: 'Chapter 1',
                            chapterNumber: 1,
                            sourceOrder: 1,
                            scanlator: null,
                            uploadDate: '0',
                            isDownloaded: true,
                            isBookmarked: false,
                            ...progress,
                        }}
                        showChapterNumber={false}
                        onSelect={vi.fn()}
                        selected={null}
                    />
                </MemoryRouter>
            </I18nProvider>,
        );
        expect(screen.getByText(expected)).toBeTruthy();
    });
});
