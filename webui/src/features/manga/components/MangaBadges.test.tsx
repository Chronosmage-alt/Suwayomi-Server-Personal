/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { i18n } from '@lingui/core';
import { I18nProvider } from '@lingui/react';
import { MangaBadges } from '@/features/manga/components/MangaBadges.tsx';

vi.mock('@/features/komga/KomgaLibrary.tsx', () => ({ useKomgaSummary: () => undefined }));
vi.mock('@/features/settings/services/ServerSettingsMetadata.ts', () => ({
    useMetadataServerSettings: () => ({ settings: { showUnreadBadge: true, showDownloadBadge: true } }),
}));
const controls = vi.hoisted(() => ({ touch: false }));
vi.mock('@/base/utils/MediaQuery.tsx', () => ({ MediaQuery: { useIsTouchDevice: () => controls.touch } }));
i18n.load('en', {});
i18n.activate('en');
afterEach(cleanup);

describe('Browse library badges', () => {
    it.each([false, true])('shows cross-entry matches on touch=%s without changing add behavior', (touch) => {
        controls.touch = touch;
        const update = vi.fn();
        render(
            <I18nProvider i18n={i18n}>
                <MangaBadges
                    mode="source"
                    inLibraryIndicator
                    hasLibraryTitle
                    isInLibrary={false}
                    updateLibraryState={update}
                />
            </I18nProvider>,
        );
        expect(screen.getByText('Title in library')).toBeTruthy();
        expect(screen.queryByText('In Library')).toBeNull();
        if (!touch) {
            fireEvent.click(screen.getByText('Add To Library'));
            expect(update).toHaveBeenCalledOnce();
        }
    });

    it('gives exact library membership priority over a title match', () => {
        controls.touch = false;
        render(
            <I18nProvider i18n={i18n}>
                <MangaBadges
                    mode="source"
                    inLibraryIndicator
                    hasLibraryTitle
                    isInLibrary
                    updateLibraryState={vi.fn()}
                />
            </I18nProvider>,
        );
        expect(screen.getByText('In Library')).toBeTruthy();
        expect(screen.getByText('Remove from the library')).toBeTruthy();
        expect(screen.queryByText('Title in library')).toBeNull();
    });

    it('does not show title matching where library indicators are disabled', () => {
        render(
            <I18nProvider i18n={i18n}>
                <MangaBadges mode="default" hasLibraryTitle isInLibrary={false} updateLibraryState={vi.fn()} />
            </I18nProvider>,
        );
        expect(screen.queryByText('Title in library')).toBeNull();
    });
});
