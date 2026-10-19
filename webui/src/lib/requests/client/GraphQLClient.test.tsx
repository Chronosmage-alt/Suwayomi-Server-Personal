/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { describe, expect, it, vi } from 'vitest';
import { GraphQLClient } from '@/lib/requests/client/GraphQLClient.ts';

vi.mock('@/features/authentication/AuthManager.ts', () => ({ AuthManager: {} }));
vi.mock('@/lib/storage/AppStorage.ts', () => ({ AppStorage: {} }));

describe('permanent subscription reconnection', () => {
    it('invokes registered restarts and removes stopped subscriptions', () => {
        // Exercise the registration/reconnection lifecycle without opening a WebSocket.
        const client = Object.create(GraphQLClient.prototype) as GraphQLClient;
        Reflect.set(client, 'activeConnectionSubscriptions', new Map());
        const updater = vi.fn();
        const downloads = vi.fn();
        const unregister = client.registerSubscription(updater);
        client.registerSubscription(downloads);
        Reflect.get(client, 'restartAllSubscriptions').call(client);
        expect(updater).toHaveBeenCalledOnce();
        expect(downloads).toHaveBeenCalledOnce();
        unregister();
        Reflect.get(client, 'restartAllSubscriptions').call(client);
        expect(updater).toHaveBeenCalledOnce();
        expect(downloads).toHaveBeenCalledTimes(2);
    });
});
