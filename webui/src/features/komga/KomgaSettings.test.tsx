/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { I18nProvider } from '@lingui/react';
import { i18n } from '@lingui/core';
import { KomgaSettings } from '@/features/komga/KomgaSettings.tsx';

const controls = vi.hoisted(() => ({
    config: {
        enabled: false,
        apiUrl: 'http://komga:25600',
        browserUrl: 'http://nas:8080',
        credentialConfigured: true,
        canManage: true,
        autoLink: false,
        showCounter: true,
    },
    save: vi.fn(),
    test: vi.fn(),
    scan: vi.fn(),
    progress: vi.fn(),
    refetch: vi.fn(),
}));
vi.mock('@/lib/requests/RequestManager.ts', () => ({
    requestManager: { graphQLClient: { client: { refetchQueries: controls.refetch, query: controls.progress } } },
}));
vi.mock('@apollo/client/react', () => ({
    useQuery: () => ({ data: { komgaSettings: controls.config } }),
    useMutation: (doc: { definitions: { name?: { value: string } }[] }) => {
        if (doc.definitions.some((d) => d.name?.value === 'TestKomga')) {
            return [controls.test, { loading: false }];
        }
        if (doc.definitions.some((d) => d.name?.value === 'ScanKomgaLibrary')) {
            return [controls.scan, { loading: false }];
        }
        return [controls.save, { loading: false }];
    },
}));
beforeEach(() => {
    vi.clearAllMocks();
    controls.config.canManage = true;
    controls.config.enabled = false;
    controls.config.autoLink = false;
    controls.config.showCounter = true;
    controls.save.mockResolvedValue({});
    controls.refetch.mockResolvedValue(undefined);
    controls.progress.mockReset();
    controls.scan.mockResolvedValue({
        data: {
            scanKomgaLibrary: {
                running: false,
                code: 'COMPLETE',
                indexedSeries: 100,
                processed: 10,
                total: 10,
                automaticallyLinked: 3,
                alreadyLinked: 2,
                ambiguous: 1,
                optedOut: 1,
                unmatched: 3,
            },
        },
    });
    controls.test.mockResolvedValue({
        data: { testKomgaConnection: { success: false, message: 'Docker route unavailable' } },
    });
    i18n.load('en', {});
    i18n.activate('en');
});
afterEach(cleanup);
const show = () =>
    render(
        <I18nProvider i18n={i18n}>
            <KomgaSettings />
        </I18nProvider>,
    );

describe('Komga connection settings', () => {
    it('uses a server-mounted credential and saves independent URLs with default disabled', async () => {
        show();
        expect((screen.getByLabelText('Automatically link matching Komga series') as HTMLInputElement).checked).toBe(
            false,
        );
        expect((screen.getByLabelText('Show blue Komga book counter') as HTMLInputElement).checked).toBe(true);
        expect(screen.queryByLabelText(/password|api key/i)).toBeNull();
        fireEvent.change(screen.getByLabelText('Browser-facing Komga URL'), {
            target: { value: 'http://nas:8080/reader' },
        });
        expect((screen.getByRole('button', { name: 'Test Connection' }) as HTMLButtonElement).disabled).toBe(true);
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));
        await waitFor(() =>
            expect(controls.save).toHaveBeenCalledWith({
                variables: {
                    enabled: false,
                    apiUrl: 'http://komga:25600',
                    browserUrl: 'http://nas:8080/reader',
                    autoLink: false,
                    showCounter: true,
                },
            }),
        );
    });
    it('shows connection failures without requiring the integration to be enabled', async () => {
        show();
        fireEvent.click(screen.getByRole('button', { name: 'Test Connection' }));
        expect(await screen.findByText('Docker route unavailable')).toBeTruthy();
        expect(controls.save).not.toHaveBeenCalled();
    });
    it('respects existing settings permissions', () => {
        controls.config.canManage = false;
        show();
        expect((screen.getByLabelText('Internal Komga API URL') as HTMLInputElement).disabled).toBe(true);
        expect((screen.getByRole('button', { name: 'Test Connection' }) as HTMLButtonElement).disabled).toBe(true);
    });
    it('allows a manual scan with automatic linking off and reports every outcome', async () => {
        controls.config.enabled = true;
        show();
        fireEvent.click(screen.getByRole('button', { name: 'Scan for matching Komga series' }));
        expect(await screen.findByRole('status')).toHaveProperty(
            'textContent',
            expect.stringContaining('Automatically linked: 3'),
        );
        expect(screen.getByRole('status').textContent).toContain('opted out: 1');
        expect(screen.getByRole('status').textContent).toContain('unmatched: 3');
        expect(controls.scan).toHaveBeenCalledTimes(1);
        expect(controls.save).not.toHaveBeenCalled();
    });
    it('continues observing identical cached progress objects until the scan finishes', async () => {
        vi.useFakeTimers();
        try {
            controls.config.enabled = true;
            const progress = {
                running: true,
                code: 'INDEXING',
                indexedSeries: 0,
                processed: 0,
                total: 0,
                automaticallyLinked: 0,
                alreadyLinked: 0,
                ambiguous: 0,
                optedOut: 0,
                unmatched: 0,
            };
            controls.scan.mockResolvedValue({ data: { scanKomgaLibrary: progress } });
            controls.progress
                .mockResolvedValueOnce({ data: { komgaScanStatus: progress } })
                .mockResolvedValueOnce({ data: { komgaScanStatus: progress } })
                .mockResolvedValueOnce({
                    data: {
                        komgaScanStatus: {
                            ...progress,
                            running: false,
                            code: 'COMPLETE',
                            total: 1,
                            processed: 1,
                            unmatched: 1,
                        },
                    },
                });
            show();
            await act(async () => {
                fireEvent.click(screen.getByRole('button', { name: 'Scan for matching Komga series' }));
            });
            await act(async () => {
                await vi.advanceTimersByTimeAsync(2000);
            });
            expect(controls.progress).toHaveBeenCalledTimes(1);
            await act(async () => {
                await vi.advanceTimersByTimeAsync(2000);
            });
            expect(controls.progress).toHaveBeenCalledTimes(2);
            await act(async () => {
                await vi.advanceTimersByTimeAsync(2000);
            });
            expect(controls.progress).toHaveBeenCalledTimes(3);
            expect(screen.getByRole('status').textContent).toContain('Scan: COMPLETE');
            expect(
                (screen.getByRole('button', { name: 'Scan for matching Komga series' }) as HTMLButtonElement).disabled,
            ).toBe(false);
        } finally {
            vi.useRealTimers();
        }
    });
});
