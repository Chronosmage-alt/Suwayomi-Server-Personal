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
import { CompletedCategorySettings } from '@/features/library/components/CompletedCategorySettings.tsx';

const controls = vi.hoisted(() => ({
    config: {
        enabled: false,
        categoryId: 42 as number | null,
        effective: false,
        destinationMissing: false,
        categories: [{ id: 42, name: 'Completed' }],
    },
    save: vi.fn(),
    organise: vi.fn(),
    preview: vi.fn(),
    refetch: vi.fn(),
}));
vi.mock('@/lib/requests/RequestManager.ts', () => ({ requestManager: { graphQLClient: { client: {} } } }));
vi.mock('@apollo/client/react', () => ({
    useQuery: () => ({ data: { completedCategorySettings: controls.config }, refetch: controls.refetch }),
    useMutation: (document: { definitions: { name?: { value: string } }[] }) => [
        document.definitions.some(({ name }) => name?.value === 'OrganiseCompletedManga')
            ? controls.organise
            : controls.save,
        { loading: false },
    ],
    useLazyQuery: () => [
        controls.preview,
        {
            loading: false,
            data: { completedCategoryPreview: { categoryId: 42, toAdd: 3, alreadyPresent: 2, skipped: 1 } },
        },
    ],
}));
i18n.load('en', {});
i18n.activate('en');
afterEach(cleanup);
beforeEach(() => {
    vi.clearAllMocks();
    controls.config = {
        enabled: false,
        categoryId: 42,
        effective: false,
        destinationMissing: false,
        categories: [{ id: 42, name: 'Completed' }],
    };
    controls.save.mockResolvedValue({});
    controls.refetch.mockResolvedValue({});
    controls.preview.mockResolvedValue({});
    controls.organise.mockResolvedValue({ data: { organiseCompletedManga: { toAdd: 3 } } });
});
const show = () =>
    render(
        <I18nProvider i18n={i18n}>
            <CompletedCategorySettings />
        </I18nProvider>,
    );
describe('Completed categorisation', () => {
    it('shows category names and explains manual removal and existing category rules', () => {
        show();
        expect(screen.getByRole('combobox').textContent).toBe('Completed');
        expect(screen.queryByText('42')).toBeNull();
        expect(screen.getByText(/Removing a title manually/).textContent).toContain(
            'prevents it from being automatically re-added',
        );
        expect(screen.getByText(/This is independent of Exclude Completed/)).toBeTruthy();
    });
    it('enables the default-user rule without any account selector', async () => {
        show();
        fireEvent.click(screen.getByRole('switch', { name: 'Automatically categorise completed manga' }));
        await waitFor(() =>
            expect(controls.save).toHaveBeenCalledWith({ variables: { enabled: true, categoryId: 42 } }),
        );
        expect(screen.getAllByRole('combobox')).toHaveLength(1);
    });
    it('previews without applying and only applies after the explicit action', async () => {
        show();
        fireEvent.click(screen.getByRole('button', { name: 'Organise existing completed manga' }));
        await screen.findByRole('dialog');
        expect(screen.getByText(/3 titles will be added/).textContent).toContain(
            '2 are already present. 1 will be skipped',
        );
        expect(controls.organise).not.toHaveBeenCalled();
        expect(controls.save).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('button', { name: 'Apply organisation' }));
        await waitFor(() => expect(controls.organise).toHaveBeenCalledWith({ variables: { categoryId: 42 } }));
        expect(await screen.findByText('Added 3 titles to the destination category.')).toBeTruthy();
    });
    it('warns for a deleted destination and blocks organisation without choosing a replacement', () => {
        controls.config = { ...controls.config, enabled: true, categoryId: null, destinationMissing: true };
        show();
        expect(screen.getByText(/destination category is unavailable/)).toBeTruthy();
        expect(
            (screen.getByRole('button', { name: 'Organise existing completed manga' }) as HTMLButtonElement).disabled,
        ).toBe(true);
        expect(controls.save).not.toHaveBeenCalled();
    });
    it('refreshes rule state when categories change on the same settings page', async () => {
        const view = render(
            <I18nProvider i18n={i18n}>
                <CompletedCategorySettings categoryRevision="before" />
            </I18nProvider>,
        );
        expect(controls.refetch).not.toHaveBeenCalled();
        view.rerender(
            <I18nProvider i18n={i18n}>
                <CompletedCategorySettings categoryRevision="after" />
            </I18nProvider>,
        );
        await waitFor(() => expect(controls.refetch).toHaveBeenCalledTimes(1));
    });
});
