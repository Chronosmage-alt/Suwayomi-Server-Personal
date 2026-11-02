/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Notifications } from '@/base/notification/Notifications.ts';

const controls = vi.hoisted(() => ({ active: true, snackbar: vi.fn(), device: vi.fn() }));
vi.mock('@/base/AppSession.ts', () => ({ AppSession: { isActivelyUsed: () => controls.active } }));
vi.mock('notistack', () => ({ enqueueSnackbar: controls.snackbar }));
vi.mock('@/lib/DeviceNotifications.ts', () => ({ DeviceNotifications: { show: controls.device } }));

beforeEach(() => {
    vi.clearAllMocks();
    controls.active = true;
    controls.device.mockResolvedValue(undefined);
});

describe('notification descriptions', () => {
    it('preserves the description and variant in the active app', () => {
        Notifications.error('Save failed', 'Connection lost', { id: 'save' });
        expect(controls.snackbar).toHaveBeenCalledWith(
            'Save failed',
            expect.objectContaining({
                key: 'save',
                variant: 'error',
                description: 'Connection lost',
            }),
        );
        expect(controls.device).not.toHaveBeenCalled();
    });

    it('uses the description as the device notification body', () => {
        controls.active = false;
        const onClick = vi.fn();
        Notifications.info('Fixture manga', '2 new chapters', { id: 'chapters', onClick });
        expect(controls.device).toHaveBeenCalledWith(
            'Fixture manga',
            expect.objectContaining({
                tag: 'chapters',
                body: '2 new chapters',
                onClick,
            }),
        );
        expect(controls.snackbar).not.toHaveBeenCalled();
    });

    it('supports options without a description', () => {
        controls.active = false;
        Notifications.success('Saved', { id: 'save' });
        expect(controls.device).toHaveBeenCalledWith('Saved', expect.objectContaining({ tag: 'save', body: 'Saved' }));
    });
});
