/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { I18nProvider } from '@lingui/react';
import { i18n } from '@lingui/core';
import type { GetAdaptiveSettingsQuery, GetSourcesListQuery } from '@/lib/graphql/generated/graphql.ts';
import { ContentWarning } from '@/lib/graphql/generated/graphql-base.types.ts';
import { AdaptiveUpdateSettings } from '@/features/settings/components/globalUpdate/AdaptiveUpdateSettings.tsx';
import { Sources } from '@/features/source/services/Sources.ts';

const controls = vi.hoisted(() => ({
    languages: ['fr'],
    showNsfw: true,
    settings: null as GetAdaptiveSettingsQuery['adaptiveUpdateSettings'] | null,
    sources: [] as GetSourcesListQuery['sources']['nodes'],
    sourceLoading: false,
    sourceError: undefined as Error | undefined,
    saveSource: vi.fn(),
    saveMaster: vi.fn(),
    refetch: vi.fn(),
}));

vi.mock('@/lib/requests/RequestManager.ts', () => ({
    requestManager: {
        graphQLClient: { client: {} },
        useGetSourceList: () => ({
            data: { sources: { nodes: controls.sources } },
            loading: controls.sourceLoading,
            error: controls.sourceError,
        }),
    },
}));
vi.mock('@apollo/client/react', () => ({
    useQuery: () => ({ data: { adaptiveUpdateSettings: controls.settings }, refetch: controls.refetch }),
    useMutation: (document: { definitions: { kind: string; name?: { value: string } }[] }) => [
        document.definitions.some((definition) => definition.name?.value === 'SET_ADAPTIVE_SOURCE')
            ? controls.saveSource
            : controls.saveMaster,
        { loading: false },
    ],
}));
vi.mock('@/features/settings/services/ServerSettingsMetadata.ts', () => ({
    useMetadataServerSettings: () => ({
        settings: { browseLanguages: controls.languages, showNsfw: controls.showNsfw },
    }),
    createUpdateMetadataServerSettings: () => vi.fn(),
}));
vi.mock('@/features/source/services/SourceMetadata.ts', () => ({
    getSourceMetadata: (source: { meta: { key: string; value: string }[] }) => ({
        isEnabled: !source.meta.some((meta) => meta.key === 'isEnabled' && meta.value === 'false'),
        isPinned: false,
    }),
}));
vi.mock('@/base/utils/Toast.ts', () => ({ makeToast: vi.fn() }));
vi.mock('@/lib/HelperFunctions.ts', () => ({ getErrorMessage: String }));

i18n.load('en', {});
i18n.activate('en');
const ui = () => (
    <I18nProvider i18n={i18n}>
        <AdaptiveUpdateSettings />
    </I18nProvider>
);

const source = (id: string, lang: string, name = 'MangaDex', pkg = 'test.mangadex') => ({
    __typename: 'SourceType' as const,
    id,
    lang,
    name,
    displayName: name,
    iconUrl: '',
    contentWarning: ContentWarning.Safe,
    isConfigurable: false,
    supportsLatest: true,
    meta: [],
    extension: { __typename: 'ExtensionType' as const, pkgName: pkg, name, storeIndexUrl: null, extensionStore: null },
});
const french = () => screen.getByRole('switch', { name: /Adaptive updates for MangaDex.*français/i });
const english = () => screen.queryByRole('switch', { name: /Adaptive updates for MangaDex.*english/i });
const showAll = () => screen.getByRole('switch', { name: 'Show all languages' });

beforeEach(() => {
    vi.clearAllMocks();
    controls.languages = ['fr'];
    controls.showNsfw = true;
    controls.sourceError = undefined;
    controls.sourceLoading = false;
    controls.sources = [
        source('9007199254740993', 'fr'),
        source('9007199254740994', 'en'),
        source('3', 'fr', 'Manga Ball', 'test.mangaball'),
        source('0', 'localsourcelang', 'Local source', 'local'),
    ];
    controls.settings = {
        __typename: 'AdaptiveUpdateSettings',
        enabled: true,
        canManage: true,
        maxBackoffDays: 14,
        sources: controls.sources.map((item) => ({
            __typename: 'AdaptiveSourceSetting',
            sourceId: item.id,
            providerPackage: item.extension.pkgName,
            name: item.name,
            enabled: item.lang === 'en',
            installed: true,
        })),
    };
    controls.refetch.mockResolvedValue(undefined);
    controls.saveSource.mockResolvedValue(undefined);
});
afterEach(cleanup);

describe('adaptive source display', () => {
    it('defaults to Browse language filtering, groups extensions and labels languages', () => {
        const filter = vi.spyOn(Sources, 'filter');
        render(ui());
        expect((showAll() as HTMLInputElement).checked).toBe(false);
        expect(french()).toBeTruthy();
        expect(english()).toBeNull();
        expect(screen.getByRole('heading', { name: 'MangaDex' })).toBeTruthy();
        expect(screen.getByRole('heading', { name: 'Manga Ball' })).toBeTruthy();
        expect(within(screen.getByRole('region', { name: 'MangaDex' })).getAllByRole('switch')).toHaveLength(1);
        expect(screen.getByRole('switch', { name: /Adaptive updates for Local source/ })).toBeTruthy();
        expect(filter).toHaveBeenCalledWith(controls.sources, {
            languages: ['fr'],
            isNsfw: undefined,
            keepLocalSource: true,
            enabled: true,
        });
    });

    it('shows other languages on demand without changing hidden saved preferences', () => {
        const before = JSON.stringify(controls.settings);
        render(ui());
        fireEvent.click(showAll());
        expect((english() as HTMLInputElement).checked).toBe(true);
        expect(within(screen.getByRole('region', { name: 'MangaDex' })).getAllByRole('switch')).toHaveLength(2);
        fireEvent.click(showAll());
        expect(english()).toBeNull();
        fireEvent.click(showAll());
        expect((english() as HTMLInputElement).checked).toBe(true);
        expect(JSON.stringify(controls.settings)).toBe(before);
        expect(controls.saveSource).not.toHaveBeenCalled();
        expect(controls.saveMaster).not.toHaveBeenCalled();
    });

    it('retains exact string source IDs and does not assign an old provider to its replacement', async () => {
        controls.settings!.sources.push({
            __typename: 'AdaptiveSourceSetting',
            sourceId: '9007199254740993',
            providerPackage: 'old.provider',
            name: 'Old MangaDex',
            enabled: true,
            installed: false,
        });
        render(ui());
        expect(screen.queryByRole('switch', { name: /Old MangaDex/ })).toBeNull();
        fireEvent.click(french());
        await waitFor(() =>
            expect(controls.saveSource).toHaveBeenCalledExactlyOnceWith({
                variables: { sourceId: '9007199254740993', enabled: true },
            }),
        );
        fireEvent.click(showAll());
        const dormant = screen.getByRole('switch', { name: /Old MangaDex.*Language unavailable/ });
        expect((dormant as HTMLInputElement).disabled).toBe(true);
        expect((dormant as HTMLInputElement).checked).toBe(true);
        expect(screen.getByRole('heading', { name: 'old.provider' })).toBeTruthy();
    });

    it('reacts to changed language preferences and uses Browse ISO language aliases', () => {
        controls.languages = ['fr-FR'];
        const view = render(ui());
        expect(french()).toBeTruthy();
        expect(english()).toBeNull();
        controls.languages = ['en'];
        view.rerender(ui());
        expect(english()).toBeTruthy();
        expect(screen.queryByRole('switch', { name: /MangaDex.*français/i })).toBeNull();
        expect(controls.saveSource).not.toHaveBeenCalled();
    });

    it('keeps Browse source and content filters even when showing every language', () => {
        controls.sources[1].meta = [
            { __typename: 'SourceMetaType', sourceId: '9007199254740994', key: 'isEnabled', value: 'false' },
        ];
        controls.sources[2].contentWarning = ContentWarning.Nsfw;
        controls.showNsfw = false;
        render(ui());
        fireEvent.click(showAll());
        expect(english()).toBeNull();
        expect(screen.queryByRole('heading', { name: 'Manga Ball' })).toBeNull();
        expect(french()).toBeTruthy();
    });

    it('handles empty language selections and preserves read-only controls', () => {
        controls.languages = [];
        controls.sources = controls.sources.filter((item) => item.id !== '0');
        controls.settings!.sources = controls.settings!.sources.filter((item) => item.sourceId !== '0');
        const view = render(ui());
        expect(screen.getByText('No sources match your Browse display settings.')).toBeTruthy();
        controls.settings!.canManage = false;
        view.rerender(ui());
        expect(screen.queryByRole('switch', { name: 'Show all languages' })).toBeNull();
        expect((screen.getByRole('switch', { name: 'Adaptive library updates' }) as HTMLInputElement).disabled).toBe(
            true,
        );
    });
});
