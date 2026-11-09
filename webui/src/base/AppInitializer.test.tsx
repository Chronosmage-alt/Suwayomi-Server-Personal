/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { AppInitializer } from '@/base/AppInitializer.ts';

const controls = vi.hoisted(() => ({
    about: vi.fn(),
    required: vi.fn(),
    downloads: vi.fn(),
    subscribe: vi.fn(),
    unsubscribe: vi.fn(),
    authenticated: false,
    queue: vi.fn(),
    notifierStart: vi.fn(),
    notifierStop: vi.fn(),
}));
vi.mock('@/lib/requests/RequestManager.ts', () => ({
    requestManager: {
        getAbout: () => ({ response: controls.about() }),
        getGlobalMeta: () => ({ response: controls.required() }),
        getServerSettings: () => ({ response: Promise.resolve() }),
        getDownloadStatus: () => ({ response: controls.downloads() }),
        getExtensionListFetch: () => ({ response: Promise.resolve() }),
        downloadSubscription: () => ({ subscribe: controls.subscribe }),
        updaterSubscription: () => ({ subscribe: controls.subscribe }),
        webUIUpdateSubscription: () => ({ subscribe: controls.subscribe }),
        syncSubscription: () => ({ subscribe: controls.subscribe }),
        processQueues: controls.queue,
    },
}));
vi.mock('@/features/authentication/AuthManager.ts', () => ({
    AuthManager: {
        isAuthInitialized: () => controls.authenticated,
        setAuthRequired: vi.fn(),
        setAuthInitialized: (value: boolean) => {
            controls.authenticated = value;
        },
    },
}));
vi.mock('@/features/migration/MigrationManager.ts', () => ({ MigrationManager: { isActive: () => true } }));
vi.mock('@/features/updates/LibraryUpdateNotifier.ts', () => ({
    LibraryUpdateNotifier: { start: controls.notifierStart, stop: controls.notifierStop },
}));
beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
    controls.authenticated = false;
    controls.about.mockResolvedValue({ data: {} });
    controls.required.mockResolvedValue(undefined);
    controls.downloads.mockResolvedValue(undefined);
    controls.notifierStart.mockResolvedValue(undefined);
    controls.subscribe.mockImplementation(() => ({ unsubscribe: controls.unsubscribe }));
});
afterEach(() => {
    AppInitializer.stop();
    vi.useRealTimers();
});

describe('application initialization', () => {
    it('does not execute queued setup after an immediate stop', async () => {
        const started = AppInitializer.start();
        AppInitializer.stop();
        await started;
        expect(controls.about).not.toHaveBeenCalled();
        expect(controls.queue).not.toHaveBeenCalled();
        expect(controls.subscribe).not.toHaveBeenCalled();
    });

    it('reports required initialization errors and clears them after recovery', async () => {
        controls.required.mockRejectedValueOnce(new Error('offline'));
        const { result, unmount } = renderHook(() => AppInitializer.useStatus());
        expect(result.current.initialized).toBe(false);
        let started: Promise<void>;
        await act(async () => {
            started = AppInitializer.start();
            await vi.advanceTimersByTimeAsync(0);
        });
        const loading = result.current.actions.find((action) => action.key === 'globalMeta');
        expect(loading?.active).toBe(true);
        expect(loading?.error).toEqual(new Error('offline'));
        expect(loading?.message).toBeDefined();
        expect(result.current.initialized).toBe(false);
        await act(async () => {
            await vi.advanceTimersByTimeAsync(5000);
            await started;
        });
        expect(result.current.initialized).toBe(true);
        expect(result.current.actions.find((action) => action.key === 'globalMeta')?.error).toBeUndefined();
        act(() => AppInitializer.stop());
        expect(result.current.initialized).toBe(false);
        expect(result.current.actions).toEqual([]);
        unmount();
    });

    it('ignores an authentication response received after stopping', async () => {
        let finish!: (value: { data: object }) => void;
        controls.about.mockReturnValue(
            new Promise((resolve) => {
                finish = resolve;
            }),
        );
        const started = AppInitializer.start();
        await vi.advanceTimersByTimeAsync(0);
        AppInitializer.stop();
        finish({ data: {} });
        await started;
        expect(controls.authenticated).toBe(false);
        expect(controls.queue).not.toHaveBeenCalled();
        expect(controls.required).not.toHaveBeenCalled();
    });

    it('opens the app while the initial download queue is still loading', async () => {
        controls.downloads.mockReturnValue(new Promise(() => {}));
        await AppInitializer.start();
        await vi.advanceTimersByTimeAsync(0);
        expect(controls.downloads).toHaveBeenCalledOnce();
        expect(controls.notifierStart).toHaveBeenCalledOnce();
        expect(controls.subscribe).toHaveBeenCalledTimes(4);
    });
    it('initializes no-login access and replaces permanent subscriptions on restart', async () => {
        await AppInitializer.start();
        await vi.advanceTimersByTimeAsync(0);
        expect(controls.queue).toHaveBeenCalledOnce();
        expect(controls.subscribe).toHaveBeenCalledTimes(4);
        await AppInitializer.start();
        await vi.advanceTimersByTimeAsync(0);
        expect(controls.unsubscribe).toHaveBeenCalledTimes(4);
        expect(controls.subscribe).toHaveBeenCalledTimes(8);
        AppInitializer.stop();
        AppInitializer.stop();
        expect(controls.unsubscribe).toHaveBeenCalledTimes(8);
    });

    it('retries failed required data without duplicating subscriptions', async () => {
        controls.required.mockRejectedValueOnce(new Error('offline'));
        const started = AppInitializer.start();
        await vi.advanceTimersByTimeAsync(5000);
        await started;
        expect(controls.required).toHaveBeenCalledTimes(2);
        expect(controls.subscribe).toHaveBeenCalledTimes(4);
        expect(controls.notifierStart).toHaveBeenCalledOnce();
    });

    it('does not restart subscriptions or notifications after stopping a retry', async () => {
        controls.required.mockRejectedValue(new Error('offline'));
        const started = AppInitializer.start();
        await vi.advanceTimersByTimeAsync(0);
        AppInitializer.stop();
        await vi.advanceTimersByTimeAsync(15000);
        await started;
        expect(controls.required).toHaveBeenCalledOnce();
        expect(controls.notifierStart).not.toHaveBeenCalled();
        expect(controls.subscribe).not.toHaveBeenCalled();
        expect(controls.unsubscribe).not.toHaveBeenCalled();
    });

    it('does not hold startup behind a slow notifier', async () => {
        controls.notifierStart.mockReturnValue(new Promise(() => {}));
        await AppInitializer.start();
        await vi.advanceTimersByTimeAsync(0);
        expect(controls.notifierStart).toHaveBeenCalledOnce();
        expect(controls.subscribe).toHaveBeenCalledTimes(4);
    });

    it('retries synchronous background failures without duplicating subscriptions', async () => {
        controls.downloads.mockImplementationOnce(() => {
            throw new Error('offline');
        });
        await AppInitializer.start();
        await vi.advanceTimersByTimeAsync(5000);
        expect(controls.downloads).toHaveBeenCalledTimes(2);
        expect(controls.subscribe).toHaveBeenCalledTimes(4);
    });
});
