/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import { useLingui } from '@lingui/react/macro';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import FormControlLabel from '@mui/material/FormControlLabel';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import {
    KOMGA_SETTINGS,
    SAVE_KOMGA_SETTINGS,
    TEST_KOMGA,
    SCAN_KOMGA,
    KOMGA_SCAN_STATUS,
} from '@/lib/graphql/komga/Komga.ts';
import type {
    KomgaSettingsQuery,
    SaveKomgaSettingsMutation,
    TestKomgaMutation,
    ScanKomgaLibraryMutation,
    KomgaScanStatusQuery,
} from '@/lib/graphql/generated/graphql.ts';

export const useKomgaSettings = () =>
    useQuery<KomgaSettingsQuery>(KOMGA_SETTINGS, {
        client: requestManager.graphQLClient.client,
        fetchPolicy: 'cache-and-network',
    });

export const KomgaSettings = () => {
    const { t } = useLingui();
    const { client } = requestManager.graphQLClient;
    const query = useKomgaSettings();
    const config = query.data?.komgaSettings;
    const [enabled, setEnabled] = useState(false);
    const [apiUrl, setApiUrl] = useState('');
    const [browserUrl, setBrowserUrl] = useState('');
    const [autoLink, setAutoLink] = useState(false);
    const [showCounter, setShowCounter] = useState(true);
    const [scanStatus, setScanStatus] = useState<KomgaScanStatusQuery['komgaScanStatus']>();
    const [scan, scanning] = useMutation<ScanKomgaLibraryMutation>(SCAN_KOMGA, { client });
    const [message, setMessage] = useState('');
    const [success, setSuccess] = useState(false);
    const [save, saving] = useMutation<SaveKomgaSettingsMutation>(SAVE_KOMGA_SETTINGS, { client });
    const [test, testing] = useMutation<TestKomgaMutation>(TEST_KOMGA, { client });
    useEffect(() => {
        if (config) {
            setEnabled(config.enabled);
            setApiUrl(config.apiUrl);
            setBrowserUrl(config.browserUrl);
            setAutoLink(config.autoLink ?? false);
            setShowCounter(config.showCounter ?? true);
        }
    }, [config]);
    useEffect(() => {
        if (!scanStatus?.running) {
            return undefined;
        }
        let cancelled = false;
        const timer = window.setTimeout(async () => {
            try {
                const result = await client.query<KomgaScanStatusQuery>({
                    query: KOMGA_SCAN_STATUS,
                    fetchPolicy: 'network-only',
                });
                if (!cancelled) {
                    // Apollo can reuse the same cached object while a scan is still active.
                    const progress = result.data?.komgaScanStatus;
                    setScanStatus(progress ? { ...progress } : undefined);
                }
            } catch {
                if (!cancelled) {
                    setScanStatus(undefined);
                    setMessage(t`Could not read scan progress. The server may still be processing.`);
                }
            }
        }, 2000);
        return () => {
            cancelled = true;
            window.clearTimeout(timer);
        };
    }, [client, scanStatus]);
    if (query.error) {
        return <Alert severity="warning">{t`Komga settings could not be loaded.`}</Alert>;
    }
    if (!config) {
        return <Typography>{t`Loading Komga settings…`}</Typography>;
    }
    const dirty =
        enabled !== config.enabled ||
        apiUrl !== config.apiUrl ||
        browserUrl !== config.browserUrl ||
        autoLink !== (config.autoLink ?? false) ||
        showCounter !== (config.showCounter ?? true);
    const busy = saving.loading || testing.loading;
    return (
        <Stack spacing={2} sx={{ p: 2, minWidth: 0 }}>
            <Typography variant="h6">Komga</Typography>
            <Typography>{t`Read-only inventory and reading progress. Komga does not change your downloads or source update schedule.`}</Typography>
            <FormControlLabel
                label={t`Enable Komga integration`}
                control={
                    <Switch
                        checked={enabled}
                        disabled={!config.canManage || busy}
                        onChange={(e) => setEnabled(e.target.checked)}
                    />
                }
            />
            <TextField
                label={t`Internal Komga API URL`}
                value={apiUrl}
                disabled={!config.canManage || busy}
                onChange={(e) => setApiUrl(e.target.value)}
                helperText={t`Use the base URL reachable from the Suwayomi server, including any subpath. Docker service DNS depends on your network configuration.`}
            />
            <FormControlLabel
                label={t`Automatically link matching Komga series`}
                control={
                    <Switch
                        checked={autoLink}
                        disabled={!config.canManage || busy}
                        onChange={(e) => setAutoLink(e.target.checked)}
                    />
                }
            />
            <FormControlLabel
                label={t`Show blue Komga book counter`}
                control={
                    <Switch
                        checked={showCounter}
                        disabled={!config.canManage || busy}
                        onChange={(e) => setShowCounter(e.target.checked)}
                    />
                }
            />
            <Typography variant="caption">{t`Counts represent stored books, not verified chapters. Manual unlinking permanently prevents automatic relinking; manually link a series to allow it again.`}</Typography>
            <Button
                disabled={
                    !config.canManage || !config.enabled || dirty || busy || scanning.loading || scanStatus?.running
                }
                onClick={async () => {
                    try {
                        const result = await scan();
                        setScanStatus(result.data?.scanKomgaLibrary);
                    } catch {
                        setMessage(t`Could not start Komga scan.`);
                    }
                }}
            >{t`Scan for matching Komga series`}</Button>
            {scanStatus && (
                <Alert severity={scanStatus.code === 'COMPLETE' ? 'success' : 'info'} role="status">
                    {t`Scan: ${scanStatus.code}. Indexed series: ${scanStatus.indexedSeries}. Processed ${scanStatus.processed} / ${scanStatus.total}. Automatically linked: ${scanStatus.automaticallyLinked}; already linked: ${scanStatus.alreadyLinked}; ambiguous or requiring review: ${scanStatus.ambiguous}; opted out: ${scanStatus.optedOut}; unmatched: ${scanStatus.unmatched}.`}
                </Alert>
            )}
            <TextField
                label={t`Browser-facing Komga URL`}
                value={browserUrl}
                disabled={!config.canManage || busy}
                onChange={(e) => setBrowserUrl(e.target.value)}
                helperText={t`Optional. Used only for Open in Komga; it can differ from the internal URL.`}
            />
            <Alert severity={config.credentialConfigured ? 'info' : 'warning'}>
                {config.credentialConfigured
                    ? t`API-key secret is configured on the server.`
                    : t`Mount the Komga API-key secret file on the server. No key is stored in this page.`}
            </Alert>
            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                <Button
                    disabled={!config.canManage || busy || !dirty}
                    onClick={async () => {
                        try {
                            await save({ variables: { enabled, apiUrl, browserUrl, autoLink, showCounter } });
                            await client.refetchQueries({ include: ['KomgaSettings', 'KomgaManga'] });
                            setSuccess(true);
                            setMessage(
                                t`Komga settings saved. Changing the internal URL requires confirming existing links again.`,
                            );
                        } catch {
                            setSuccess(false);
                            setMessage(t`Could not save Komga settings. Check the URLs and permissions.`);
                        }
                    }}
                >{t`Save`}</Button>
                <Button
                    disabled={!config.canManage || busy || dirty || !config.apiUrl}
                    onClick={async () => {
                        try {
                            const result = await test();
                            setSuccess(!!result.data?.testKomgaConnection.success);
                            setMessage(result.data?.testKomgaConnection.message ?? t`Connection test failed.`);
                        } catch {
                            setSuccess(false);
                            setMessage(t`Connection test failed.`);
                        }
                    }}
                >{t`Test Connection`}</Button>
            </Stack>
            {dirty && <Typography variant="caption">{t`Save settings before testing the connection.`}</Typography>}
            {message && <Alert severity={success ? 'success' : 'warning'}>{message}</Alert>}
        </Stack>
    );
};
