/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { requestManager } from '@/lib/requests/RequestManager.ts';
import { d } from 'koration';
import { ControlledPromise } from '@/lib/ControlledPromise.ts';
import { AuthManager } from '@/features/authentication/AuthManager.ts';
import { assertIsDefined } from '@/base/Asserts.ts';
import { MigrationManager } from '@/features/migration/MigrationManager.ts';
import { defaultPromiseErrorHandler } from '@/lib/DefaultPromiseErrorHandler.ts';
import { LibraryUpdateNotifier } from '@/features/updates/LibraryUpdateNotifier.ts';
import { BasicAuthDeprecationReminder } from '@/base/notification/BasicAuthDeprecationReminder.ts';
import type { MessageDescriptor } from '@lingui/core';
import { useSyncExternalStore } from 'react';
import { msg } from '@lingui/core/macro';
import { getUniqueId } from '@/lib/HelperFunctions.ts';

type Action = {
    key: string;
    message?: MessageDescriptor;
    errorMessage?: MessageDescriptor;
    required: boolean;
    setup: () => Promise<unknown> | unknown;
    active?: boolean;
    error?: unknown;
};

type Status = {
    initialized: boolean;
    actions: Omit<Action, 'setup'>[];
};

type Config = readonly (
    | Action
    | ((Omit<Action, 'required'> & { required: false })[] | (Omit<Action, 'required'> & { required: true })[])
)[];

export class AppInitializer {
    private static generation = 0;

    private static initialized = false;

    private static actions = new Map<string, Action & { controller: ControlledPromise }>();

    private static subscriptions: any[] = [];

    private static subscribers = new Map<string, () => void>();

    private static notifierValue: string = '';

    private static config: Config = [
        {
            key: 'initializeAuthentication',
            message: msg`Checking for authentication…`,
            errorMessage: msg`Unable to determine authentication`,
            required: true,
            setup: async () => {
                const { generation } = AppInitializer;
                const response = await requestManager.getAbout().response;

                if (generation !== AppInitializer.generation || AuthManager.isAuthInitialized()) {
                    return;
                }

                assertIsDefined(response?.data);

                AuthManager.setAuthRequired(false);
                AuthManager.setAuthInitialized(true);
                requestManager.processQueues();
            },
        },
        [
            {
                key: 'globalMeta',
                message: msg`Loading client settings…`,
                errorMessage: msg`Unable to load client settings`,
                required: true,
                setup: () => requestManager.getGlobalMeta().response,
            },
            {
                key: 'serverSettings',
                message: msg`Loading server settings…`,
                errorMessage: msg`Unable to load server settings`,
                required: true,
                setup: () => requestManager.getServerSettings().response,
            },
        ],
        [
            {
                key: 'resumeMigration',
                required: false,
                setup: () => {
                    if (!MigrationManager.isActive()) {
                        navigator.locks
                            ?.request('migration-executor', async () => {
                                const resumed = await MigrationManager.resume();
                                if (resumed) {
                                    await MigrationManager.awaitCompletion();
                                }
                            })
                            .catch(defaultPromiseErrorHandler('ResumeMigration'));
                    }
                },
            },
            {
                key: 'libraryUpdateNotifier',
                required: false,
                setup: () => LibraryUpdateNotifier.start(),
            },
            // Load the full download status once on startup to fill the cache
            {
                key: 'downloadStatus',
                required: false,
                setup: () => requestManager.getDownloadStatus().response,
            },
            // Fetch extension list on startup to show up-to-date number of available extension updates in the navigation bar
            // without having to open the extensions page.
            {
                key: 'fetchExtensionList',
                required: false,
                setup: () => requestManager.getExtensionListFetch().response,
            },
            /*
             * Creates permanent subscriptions to always have the latest data.
             *
             * E.g. in case a view is open, which does not subscribe to the download updates, finished downloads are never received
             * and thus, data of existing chapters/mangas in the cache get outdated
             */
            {
                key: 'downloadSubscription',
                required: false,
                setup: () => this.subscriptions.push(requestManager.downloadSubscription().subscribe()),
            },
            {
                key: 'updaterSubscription',
                required: false,
                setup: () => this.subscriptions.push(requestManager.updaterSubscription().subscribe()),
            },
            {
                key: 'webUISubscription',
                required: false,
                setup: () => this.subscriptions.push(requestManager.webUIUpdateSubscription().subscribe()),
            },
            { key: 'basicAuthDeprecationReminder', required: false, setup: () => BasicAuthDeprecationReminder.start() },
        ],
    ];

    static async start(): Promise<void> {
        AppInitializer.stop();
        const { generation } = AppInitializer;

        for (const actions of AppInitializer.config) {
            // oxlint-disable-next-line no-await-in-loop
            await AppInitializer.startActions(actions);
            if (generation !== AppInitializer.generation) {
                return;
            }
        }
        AppInitializer.initialized = true;
        AppInitializer.notify();
    }

    static stop(): void {
        AppInitializer.generation += 1;
        AppInitializer.initialized = false;
        AppInitializer.actions.forEach(({ controller }) => controller.resolve());
        AppInitializer.actions.clear();
        AppInitializer.subscriptions.forEach((subscription) => subscription.unsubscribe());
        AppInitializer.subscriptions = [];
        LibraryUpdateNotifier.stop();
        AppInitializer.notify();
    }

    private static subscribe(callback: () => void): () => void {
        const key = getUniqueId();
        AppInitializer.subscribers.set(key, callback);

        return () => this.unsubscribe(key);
    }

    private static unsubscribe(key: string): void {
        AppInitializer.subscribers.delete(key);
    }

    private static notify(): void {
        AppInitializer.notifierValue = getUniqueId();
        AppInitializer.subscribers.forEach((callback) => callback());
    }

    public static useStatus(): Status {
        useSyncExternalStore(AppInitializer.subscribe.bind(AppInitializer), () => AppInitializer.notifierValue);

        return {
            initialized: AppInitializer.initialized,
            actions: [...AppInitializer.actions.values()].map(
                ({ key, message, errorMessage, error, required, active }) => ({
                    key,
                    message,
                    errorMessage,
                    error,
                    required,
                    active,
                }),
            ),
        };
    }

    private static async startActions(actions: Action | Action[]): Promise<void[]> {
        if (Array.isArray(actions)) {
            const areRequired = actions[0].required;

            const promise = Promise.all(actions.map((action) => AppInitializer.executeActions(action)));

            if (!areRequired) {
                return Promise.resolve([]);
            }

            await promise;

            return promise;
        }

        const promise = await AppInitializer.executeActions(actions);

        return [promise];
    }

    private static async executeActions(action: Action): Promise<void> {
        const { generation } = AppInitializer;
        const controller = new ControlledPromise();
        const current = { ...action, controller, active: true };
        AppInitializer.actions.set(action.key, current);
        AppInitializer.notify();
        let timeout = d(5).seconds.inWholeMilliseconds;
        while (generation === AppInitializer.generation) {
            // Each retry depends on the previous attempt.
            // oxlint-disable-next-line no-await-in-loop
            const result = await Promise.race([
                Promise.resolve()
                    .then(() => {
                        if (generation === AppInitializer.generation) {
                            return action.setup();
                        }
                        return undefined;
                    })
                    .then(
                        () => ({ succeeded: true as const }),
                        (error: unknown) => ({ succeeded: false as const, error }),
                    ),
                controller.promise.then(() => undefined),
            ]);
            if (!result || generation !== AppInitializer.generation) {
                return;
            }
            if (result.succeeded) {
                current.active = false;
                current.error = undefined;
                AppInitializer.notify();
                controller.resolve();
                return;
            }
            current.error = result.error;
            AppInitializer.notify();
            const retryDelay = timeout;
            // oxlint-disable-next-line no-await-in-loop
            await new Promise<void>((resolve) => {
                const timer = setTimeout(resolve, retryDelay);
                void controller.promise.then(() => {
                    clearTimeout(timer);
                    resolve();
                });
            });
            timeout = Math.min(timeout * 1.5, d(2).minutes.inWholeMilliseconds);
        }
    }
}
