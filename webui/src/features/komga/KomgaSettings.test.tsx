/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
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
    },
    save: vi.fn(),
    test: vi.fn(),
    refetch: vi.fn(),
}));
vi.mock('@/lib/requests/RequestManager.ts', () => ({
    requestManager: { graphQLClient: { client: { refetchQueries: controls.refetch } } },
}));
vi.mock('@apollo/client/react', () => ({
    useQuery: () => ({ data: { komgaSettings: controls.config } }),
    useMutation: (doc: { definitions: { name?: { value: string } }[] }) => [
        doc.definitions.some((d) => d.name?.value === 'TestKomga') ? controls.test : controls.save,
        { loading: false },
    ],
}));
beforeEach(() => {
    vi.clearAllMocks();
    controls.config.canManage = true;
    controls.config.enabled = false;
    controls.save.mockResolvedValue({});
    controls.refetch.mockResolvedValue(undefined);
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
        expect(screen.queryByLabelText(/password|api key/i)).toBeNull();
        fireEvent.change(screen.getByLabelText('Browser-facing Komga URL'), {
            target: { value: 'http://nas:8080/reader' },
        });
        expect((screen.getByRole('button', { name: 'Test Connection' }) as HTMLButtonElement).disabled).toBe(true);
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));
        await waitFor(() =>
            expect(controls.save).toHaveBeenCalledWith({
                variables: { enabled: false, apiUrl: 'http://komga:25600', browserUrl: 'http://nas:8080/reader' },
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
});
