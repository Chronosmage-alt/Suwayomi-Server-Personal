/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import '@/lib/dayjs/Setup.ts';
import '@/lib/koration/Setup.ts';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject } from 'rxjs';
import { i18n } from '@lingui/core';
import { LibraryUpdateNotifier } from '@/features/notifications/services/LibraryUpdateNotifier.ts';

const controls = vi.hoisted(() => ({
    settings: { notifyNewChapters: true, notifyNewChaptersLastNotifiedAt: 100 },
    watch: vi.fn(),
    chapters: vi.fn(),
    mangas: vi.fn(),
    save: vi.fn(),
    notify: vi.fn(),
}));
vi.mock('@/lib/requests/RequestManager.ts', () => ({
    requestManager: {
        graphQLClient: { client: { watchFragment: controls.watch } },
        getChapters: (...args: unknown[]) => ({ response: controls.chapters(...args) }),
        getMangas: (...args: unknown[]) => ({ response: controls.mangas(...args) }),
    },
}));
vi.mock('@/features/settings/services/ServerSettingsMetadata.ts', () => ({
    getMetadataServerSettings: async () => controls.settings,
    updateMetadataServerSettings: controls.save,
}));
vi.mock('@/features/notifications/services/Notifications.ts', () => ({ Notifications: { show: controls.notify } }));
vi.mock('@/features/authentication/AuthManager.ts', () => ({ AuthManager: { isAuthRequired: () => false } }));
vi.mock('@/features/manga/services/Mangas.ts', () => ({ Mangas: { getThumbnailUrl: () => '' } }));
vi.mock('@/lib/react-router/ReactRouter.ts', () => ({ ReactRouter: { navigate: vi.fn() } }));

let status: Subject<{ data: { isRunning?: boolean; skippedMangasCount?: number } }>;
const result = (nodes: unknown[] = [], totalCount = nodes.length) => ({
    data: { chapters: { nodes, totalCount, pageInfo: { hasNextPage: false } } },
});
const chapter = { id: 1, fetchedAt: '110', manga: { id: 42, title: 'Fixture manga', thumbnailUrl: null } };
beforeEach(() => {
    vi.clearAllMocks();
    status = new Subject();
    controls.watch.mockReturnValue(status);
    controls.settings = { notifyNewChapters: true, notifyNewChaptersLastNotifiedAt: 100 };
    controls.chapters.mockResolvedValue(result());
    controls.mangas.mockResolvedValue({ data: { mangas: { nodes: [{ id: 42, meta: [] }] } } });
    controls.save.mockResolvedValue(undefined);
    controls.notify.mockResolvedValue(undefined);
    i18n.load('en', {});
    i18n.activate('en');
});
afterEach(() => LibraryUpdateNotifier.stop());
const settled = async () => {
    await vi.waitFor(() => expect(controls.save).toHaveBeenCalled());
};

describe('chapter notifications with adaptive updates', () => {
    it('makes no requests or subscriptions while disabled', async () => {
        controls.settings.notifyNewChapters = false;
        await LibraryUpdateNotifier.start();
        expect(controls.watch).not.toHaveBeenCalled();
        expect(controls.chapters).not.toHaveBeenCalled();
        expect(controls.mangas).not.toHaveBeenCalled();
    });

    it('ignores incomplete state and waits until an update is finished', async () => {
        await LibraryUpdateNotifier.start();
        status.next({ data: {} });
        status.next({ data: { isRunning: true } });
        expect(controls.chapters).not.toHaveBeenCalled();
        status.next({ data: { isRunning: false, skippedMangasCount: 120 } });
        await settled();
        expect(controls.notify).not.toHaveBeenCalled();
        expect(controls.chapters).toHaveBeenCalledOnce();
        expect(controls.chapters.mock.calls[0][1].filter).toMatchObject({
            inLibrary: { equalTo: true },
            isRead: { equalTo: false },
            fetchedAt: { greaterThan: '100' },
        });
    });

    it('reports stored new chapters and preserves updater state', async () => {
        controls.chapters.mockResolvedValueOnce(result([chapter])).mockResolvedValueOnce(result([], 1));
        await LibraryUpdateNotifier.start();
        const data = { isRunning: false, skippedMangasCount: 9 };
        status.next({ data });
        await vi.waitFor(() => expect(controls.notify).toHaveBeenCalledOnce());
        expect(controls.notify.mock.calls[0][0]).toBe('Fixture manga');
        expect(data).toEqual({ isRunning: false, skippedMangasCount: 9 });
        expect(controls.save).toHaveBeenCalledWith('notifyNewChaptersLastNotifiedAt', 110);
    });

    it('does not notify an initial chapter import', async () => {
        controls.chapters.mockResolvedValueOnce(result([chapter])).mockResolvedValueOnce(result([], 0));
        await LibraryUpdateNotifier.start();
        status.next({ data: { isRunning: false } });
        await settled();
        expect(controls.notify).not.toHaveBeenCalled();
    });

    it('ignores excluded scanlators and still advances past their chapters', async () => {
        controls.chapters
            .mockResolvedValueOnce(result([{ ...chapter, scanlator: 'Hidden' }]))
            .mockResolvedValueOnce(result([], 1));
        controls.mangas.mockResolvedValue({
            data: { mangas: { nodes: [{ id: 42, meta: [{ key: 'webUI_excludedScanlators', value: '["Hidden"]' }] }] } },
        });
        await LibraryUpdateNotifier.start();
        status.next({ data: { isRunning: false } });
        await settled();
        expect(controls.notify).not.toHaveBeenCalled();
        expect(controls.save).toHaveBeenCalledWith('notifyNewChaptersLastNotifiedAt', 110);
        expect(controls.mangas.mock.calls[0][1]).toEqual({ filter: { id: { in: [42] } }, first: 1 });
    });

    it('keeps allowed chapters across pages and fetches preferences once per manga', async () => {
        controls.chapters
            .mockResolvedValueOnce({
                data: {
                    chapters: {
                        nodes: [{ ...chapter, scanlator: 'Hidden' }],
                        pageInfo: { hasNextPage: true, endCursor: 'next' },
                    },
                },
            })
            .mockResolvedValueOnce(result([], 1))
            .mockResolvedValueOnce(result([{ ...chapter, id: 2, fetchedAt: '120', scanlator: 'Allowed' }]));
        controls.mangas.mockResolvedValue({
            data: { mangas: { nodes: [{ id: 42, meta: [{ key: 'webUI_excludedScanlators', value: '["Hidden"]' }] }] } },
        });
        await LibraryUpdateNotifier.start();
        status.next({ data: { isRunning: false } });
        await vi.waitFor(() => expect(controls.notify).toHaveBeenCalledOnce());
        expect(controls.notify.mock.calls[0][1].body).toBe('1 new chapter');
        expect(controls.mangas).toHaveBeenCalledOnce();
        expect(controls.save).toHaveBeenCalledWith('notifyNewChaptersLastNotifiedAt', 120);
    });

    it('discards an in-flight result after disabling', async () => {
        let resolve!: (value: ReturnType<typeof result>) => void;
        controls.chapters.mockReturnValueOnce(
            new Promise((done) => {
                resolve = done;
            }),
        );
        await LibraryUpdateNotifier.start();
        status.next({ data: { isRunning: false } });
        await vi.waitFor(() => expect(controls.chapters).toHaveBeenCalledOnce());
        await LibraryUpdateNotifier.disable();
        resolve(result());
        await new Promise((done) => {
            setTimeout(done, 0);
        });
        expect(controls.notify).not.toHaveBeenCalled();
        expect(controls.save).toHaveBeenCalledTimes(1);
        expect(controls.save).toHaveBeenCalledWith('notifyNewChapters', false);
    });
});
