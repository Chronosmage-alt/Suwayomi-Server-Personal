/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render } from '@testing-library/react';
import { Migration } from '@/features/migration/screens/Migration.tsx';
import { MigrationPhase } from '@/features/migration/Migration.types.ts';

const controls = vi.hoisted(() => ({
    phase: 'IDLE',
    pathname: '/migrate',
    resumable: false,
    back: vi.fn(),
    previous: vi.fn(),
    reset: vi.fn(),
    navigate: vi.fn(),
}));
vi.mock('@/features/migration/MigrationManager.ts', () => ({
    MigrationManager: {
        usePhase: () => controls.phase,
        isResumablePhase: () => controls.resumable,
        goToPreviousPhase: controls.previous,
        reset: controls.reset,
    },
}));
vi.mock('@/base/contexts/AppPageHistoryContext.tsx', () => ({
    useAppPageHistoryContext: () => ({ setOnBack: controls.back }),
}));
vi.mock('@/lib/utils/SubpathUtil.ts', () => ({ SubpathUtil: { getPathname: () => controls.pathname } }));
vi.mock('@/lib/react-router/ReactRouter.ts', () => ({ ReactRouter: { navigate: controls.navigate } }));
vi.mock('@/features/migration/screens/MigrationSelectSource.tsx', () => ({
    MigrationSelectSource: () => <p>Select source</p>,
}));
vi.mock('@/features/migration/screens/MigrationSelectMangas.tsx', () => ({ MigrationSelectMangas: () => null }));
vi.mock('@/features/migration/screens/MigrationSelectDestinationSources.tsx', () => ({
    MigrationSelectDestinationSources: () => null,
}));
vi.mock('@/features/migration/screens/MigrationSearch.tsx', () => ({ MigrationSearch: () => null }));
vi.mock('@/features/migration/screens/MigrationExecute.tsx', () => ({ MigrationExecute: () => null }));
beforeEach(() => {
    vi.clearAllMocks();
    controls.phase = MigrationPhase.IDLE;
    controls.pathname = '/migrate';
    controls.resumable = false;
});
afterEach(cleanup);

describe('migration navigation', () => {
    it('releases the back handler when leaving execution so browse back navigation works', () => {
        const view = render(<Migration />);
        const [[onBack]] = controls.back.mock.calls;
        onBack();
        expect(controls.previous).toHaveBeenCalledOnce();
        view.unmount();
        expect(controls.back).toHaveBeenLastCalledWith(null);
    });
    it('resumes an active migration from the browse tab', () => {
        controls.pathname = '/browse';
        controls.resumable = true;
        controls.phase = MigrationPhase.SEARCHING;
        render(<Migration />);
        expect(controls.navigate).toHaveBeenCalledWith('/migrate', { replace: true });
        expect(controls.back).not.toHaveBeenCalled();
    });
});
