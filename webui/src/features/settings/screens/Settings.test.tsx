/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { I18nProvider } from '@lingui/react';
import { i18n } from '@lingui/core';
import { Settings, SettingsIndex } from '@/features/settings/screens/Settings.tsx';

const viewport = vi.hoisted(() => ({ mobile: false }));
vi.mock('@/base/utils/MediaQuery.tsx', () => ({
    MediaQuery: {
        useIsMobileWidth: () => viewport.mobile,
        useIsDesktopWidth: () => !viewport.mobile,
    },
}));
vi.mock('@/features/navigation-bar/hooks/useAppTitle.ts', () => ({ useAppTitle: vi.fn() }));
vi.mock('@/features/navigation-bar/NavbarContext.tsx', () => ({ useNavBarContext: () => ({ bottomBarHeight: 0 }) }));
vi.mock('@/base/OffsetComponent.tsx', () => ({
    OffsetComponent: ({ children }: { children: React.ReactNode }) => <aside>{children}</aside>,
    useOffsetComponent: () => ({ topOffset: 64 }),
}));
beforeEach(() => {
    i18n.load('en', {});
    i18n.activate('en');
    viewport.mobile = false;
});
afterEach(cleanup);
const show = (entry = '/settings') =>
    render(
        <I18nProvider i18n={i18n}>
            <MemoryRouter initialEntries={[entry]}>
                <Routes>
                    <Route path="/settings" element={<Settings />}>
                        <Route index element={<SettingsIndex />} />
                        <Route path="appearance" element={<p>Appearance content</p>} />
                        <Route path="library" element={<p>Library custom controls</p>} />
                        <Route path="tracking" element={<p>Existing tracking providers</p>} />
                    </Route>
                </Routes>
            </MemoryRouter>
        </I18nProvider>,
    );

describe('responsive settings navigation', () => {
    it('opens the first pane on desktop and retains the menu while navigating', async () => {
        show();
        expect(await screen.findByText('Appearance content')).toBeTruthy();
        fireEvent.click(screen.getByRole('link', { name: 'Library' }));
        expect(await screen.findByText('Library custom controls')).toBeTruthy();
        expect(screen.getByRole('link', { name: 'Tracking' })).toBeTruthy();
        fireEvent.click(screen.getByRole('link', { name: 'Tracking' }));
        expect(await screen.findByText('Existing tracking providers')).toBeTruthy();
    });

    it('shows the menu first on mobile then gives the selected page the full area', async () => {
        viewport.mobile = true;
        show();
        expect(screen.queryByText('Appearance content')).toBeNull();
        fireEvent.click(screen.getByRole('link', { name: 'Library' }));
        expect(await screen.findByText('Library custom controls')).toBeTruthy();
        expect(screen.queryByRole('link', { name: 'Tracking' })).toBeNull();
    });

    it('retains a directly opened tracking page', () => {
        show('/settings/tracking');
        expect(screen.getByText('Existing tracking providers')).toBeTruthy();
    });
});
