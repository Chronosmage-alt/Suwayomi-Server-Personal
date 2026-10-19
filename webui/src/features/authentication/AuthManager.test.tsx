/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthManager } from '@/features/authentication/AuthManager.ts';

const storage = vi.hoisted(() => new Map<string, string>());
vi.mock('@/lib/storage/AppStorage.ts', () => ({
    AppStorage: {
        local: {
            getItemParsed: (key: string) => storage.get(key) ?? null,
            setItem: (key: string, value?: string) => {
                if (value) {
                    storage.set(key, value);
                } else {
                    storage.delete(key);
                }
            },
        },
    },
}));
beforeEach(() => {
    storage.clear();
    AuthManager.removeAccessToken();
    AuthManager.setIsRefreshingToken(false);
    AuthManager.setAuthInitialized(true);
    AuthManager.setAuthRequired(false);
});

describe('authentication compatibility', () => {
    it('does not queue or refresh tokens in a no-login installation', () => {
        AuthManager.setRefreshToken('stale-browser-token');
        expect(AuthManager.shouldRefreshAccessToken()).toBe(false);
        expect(AuthManager.shouldQueueRequests()).toBe(false);
    });
    it('refreshes an expired authenticated session once', () => {
        AuthManager.setAuthRequired(true);
        AuthManager.setRefreshToken('fixture-refresh-token');
        expect(AuthManager.shouldRefreshAccessToken()).toBe(true);
        AuthManager.setIsRefreshingToken(true);
        expect(AuthManager.shouldRefreshAccessToken()).toBe(false);
        expect(AuthManager.shouldQueueRequests()).toBe(true);
    });
    it('does not refresh a valid access token', () => {
        AuthManager.setAuthRequired(true);
        AuthManager.setRefreshToken('fixture-refresh-token');
        const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 }));
        AuthManager.setAccessToken(`${btoa('{}')}.${payload}.fixture`);
        expect(AuthManager.shouldRefreshAccessToken()).toBe(false);
        expect(AuthManager.shouldQueueRequests()).toBe(false);
    });
});
