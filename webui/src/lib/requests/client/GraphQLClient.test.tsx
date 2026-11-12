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
vi.mock('@/base/AppSession.ts', () => ({ AppSession: {} }));

describe('permanent subscription reconnection', () => {
    it('registers and cleans up subscriptions without secure-context UUID support', () => {
        vi.stubGlobal('crypto', { randomUUID: undefined });
        const client = Object.create(GraphQLClient.prototype) as GraphQLClient;
        Reflect.set(client, 'activeConnectionSubscriptions', new Map());
        const first = vi.fn();
        const second = vi.fn();
        const unregister = client.registerSubscription(first);
        client.registerSubscription(second);
        Reflect.get(client, 'restartAllSubscriptions').call(client);
        expect(first).toHaveBeenCalledOnce();
        expect(second).toHaveBeenCalledOnce();
        unregister();
        Reflect.get(client, 'restartAllSubscriptions').call(client);
        expect(first).toHaveBeenCalledOnce();
        expect(second).toHaveBeenCalledTimes(2);
        vi.unstubAllGlobals();
    });

    it('waits for disposal and the replacement socket before restarting subscriptions', async () => {
        const client = Object.create(GraphQLClient.prototype) as GraphQLClient;
        let finishDisposal!: () => void;
        const dispose = vi.fn(
            () =>
                new Promise<void>((resolve) => {
                    finishDisposal = resolve;
                }),
        );
        const on = vi.fn();
        const create = vi.fn(() => Reflect.set(client, 'wsClient', { on }));
        const restart = vi.fn();
        Reflect.set(client, 'wsClient', { dispose });
        Reflect.set(client, 'createWSClient', create);
        Reflect.set(client, 'createLink', () => 'replacement');
        Reflect.set(client, 'client', { setLink: vi.fn() });
        Reflect.set(client, 'restartAllSubscriptions', restart);
        Reflect.get(client, 'resetWsClient').call(client, true);
        expect(create).not.toHaveBeenCalled();
        finishDisposal();
        await Promise.resolve();
        expect(create).toHaveBeenCalledWith(false);
        expect(restart).not.toHaveBeenCalled();
        expect(on).toHaveBeenCalledWith('opened', expect.any(Function));
        on.mock.calls[0][1]();
        expect(restart).toHaveBeenCalledOnce();
    });

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
