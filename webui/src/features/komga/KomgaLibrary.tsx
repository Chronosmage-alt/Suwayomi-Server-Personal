/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/* Sequential requests deliberately bound concurrency and observe only active jobs. */
/* eslint-disable no-await-in-loop */
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import { KOMGA_SUMMARIES, KOMGA_SCAN_STATUS } from '@/lib/graphql/komga/Komga.ts';
import type { KomgaLibrarySummariesQuery, KomgaScanStatusQuery } from '@/lib/graphql/generated/graphql.ts';
import { useKomgaSettings } from '@/features/komga/KomgaSettings.tsx';

type Summary = KomgaLibrarySummariesQuery['komgaLibrarySummaries'][number];
const EMPTY: ReadonlyMap<number, Summary> = new Map();
const Context = createContext<ReadonlyMap<number, Summary>>(new Map());
export const useKomgaSummary = (mangaId?: number) => useContext(Context).get(mangaId ?? -1);

/** One provider per grid. Cards consume local context and never fetch inventory. */
export const KomgaLibrary = ({
    children,
    mangaIds,
    active = true,
}: {
    children: ReactNode;
    mangaIds: number[];
    active?: boolean;
}) => {
    const { client } = requestManager.graphQLClient;
    const settings = useKomgaSettings();
    const enabled =
        active &&
        !!settings.data?.komgaSettings.enabled &&
        (!!settings.data?.komgaSettings.showCounter || !!settings.data?.komgaSettings.autoLink);
    const [summaries, setSummaries] = useState<ReadonlyMap<number, Summary>>(new Map());
    const destination = settings.data?.komgaSettings.apiUrl ?? '';
    const [loadedFor, setLoadedFor] = useState<string>();
    const idsKey = [...new Set(mangaIds)].sort((a, b) => a - b).join(',');
    useEffect(() => {
        let cancelled = false;
        if (!enabled) {
            setSummaries(new Map());
            return undefined;
        }
        const ids = idsKey ? idsKey.split(',').map(Number) : [];
        const fetch = async () => {
            const next = new Map<number, Summary>();
            for (let offset = 0; offset < ids.length && !cancelled; offset += 200) {
                const response = await client.query<KomgaLibrarySummariesQuery>({
                    query: KOMGA_SUMMARIES,
                    variables: { mangaIds: ids.slice(offset, offset + 200) },
                    fetchPolicy: 'network-only',
                });
                response.data?.komgaLibrarySummaries.forEach((summary) => next.set(summary.mangaId, summary));
                if (!cancelled) {
                    setSummaries(new Map(next));
                    setLoadedFor(destination);
                }
            }
            if (!cancelled) {
                setSummaries(next);
            }
            return [...next.values()].some((summary) => summary.stale || summary.code === 'LOADING');
        };
        // Bounded observation of an active demand-driven refresh only; never a scheduled poll.
        const update = async () => {
            const pending = await fetch();
            if (!pending) {
                return;
            }
            for (let attempt = 0; attempt < 65 && !cancelled; attempt += 1) {
                const result = await client.query<KomgaScanStatusQuery>({
                    query: KOMGA_SCAN_STATUS,
                    fetchPolicy: 'network-only',
                });
                if (!result.data?.komgaScanStatus.running) {
                    await fetch();
                    break;
                }
                await new Promise<void>((resolve) => {
                    window.setTimeout(resolve, 2000);
                });
            }
        };
        update().catch(() => {
            /* Keep last successfully loaded data; cards remain usable. */
        });
        return () => {
            cancelled = true;
        };
    }, [client, enabled, idsKey, destination, settings.data?.komgaSettings.showCounter]);
    return (
        <Context
            value={enabled && loadedFor === destination && settings.data?.komgaSettings.showCounter ? summaries : EMPTY}
        >
            {children}
        </Context>
    );
};
