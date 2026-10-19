/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { I18nProvider } from '@lingui/react';
import { i18n } from '@lingui/core';
import { MangaBadges } from '@/features/manga/components/MangaBadges.tsx';

const controls = vi.hoisted(() => ({
    summary: undefined as undefined | { bookCount: number | null; code: string; stale: boolean; provenance: string },
    touch: false,
}));
vi.mock('@/features/komga/KomgaLibrary.tsx', () => ({ useKomgaSummary: () => controls.summary }));
vi.mock('@/features/settings/services/ServerSettingsMetadata.ts', () => ({
    useMetadataServerSettings: () => ({ settings: { showUnreadBadge: true, showDownloadBadge: true } }),
}));
vi.mock('@/base/utils/MediaQuery.tsx', () => ({ MediaQuery: { useIsTouchDevice: () => controls.touch } }));
beforeEach(() => {
    controls.summary = undefined;
    controls.touch = false;
    i18n.load('en', {});
    i18n.activate('en');
});
afterEach(cleanup);
const show = () =>
    render(
        <I18nProvider i18n={i18n}>
            <MangaBadges
                mangaId={1}
                mode="default"
                unread={3}
                downloadCount={5}
                isInLibrary
                updateLibraryState={() => {}}
            />
        </I18nProvider>,
    );

describe('Komga library badges shared by grid and list cards', () => {
    it.each([false, true])('preserves existing counters on desktop/touch=%s', (touch) => {
        controls.touch = touch;
        controls.summary = { bookCount: 12345, code: 'READY', stale: false, provenance: 'AUTOMATIC' };
        show();
        expect(screen.getByText('3')).toBeTruthy();
        expect(screen.getByText('5')).toBeTruthy();
        expect(window.getComputedStyle(screen.getByText('3').parentElement!).flexWrap).toBe('wrap');
        expect(screen.getByLabelText('Komga: 12345 stored books')).toBeTruthy();
    });
    it('hides unknown and unlinked counts without inventing zero', () => {
        show();
        expect(screen.queryByLabelText(/Komga:/)).toBeNull();
    });
    it('shows true zero and explicitly marks stale books', () => {
        controls.summary = { bookCount: 0, code: 'STALE', stale: true, provenance: 'MANUAL' };
        show();
        expect(screen.getByLabelText('Komga: 0 stored books (stale)')).toBeTruthy();
        expect(screen.getByLabelText('Komga: 0 stored books (stale)').textContent).toContain('0*');
    });
    it('distinguishes links requiring review from a known book count', () => {
        controls.summary = { bookCount: null, code: 'REVIEW', stale: false, provenance: 'AUTOMATIC' };
        show();
        expect(screen.getByLabelText('Komga link requires review')).toBeTruthy();
        expect(screen.queryByLabelText(/Komga:/)).toBeNull();
    });
});
