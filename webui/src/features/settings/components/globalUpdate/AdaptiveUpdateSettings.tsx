/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useQuery, useMutation } from '@apollo/client/react';
import { useLingui } from '@lingui/react/macro';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import {
    GET_ADAPTIVE_SETTINGS,
    SET_ADAPTIVE_ENABLED,
    SET_ADAPTIVE_SOURCE,
} from '@/lib/graphql/adaptive/AdaptiveUpdates.ts';
import type {
    GetAdaptiveSettingsQuery,
    SetAdaptiveEnabledMutation,
    SetAdaptiveEnabledMutationVariables,
    SetAdaptiveSourceMutation,
    SetAdaptiveSourceMutationVariables,
} from '@/lib/graphql/generated/graphql.ts';
import { makeToast } from '@/base/utils/Toast.ts';
import { getErrorMessage } from '@/lib/HelperFunctions.ts';

export const AdaptiveUpdateSettings = () => {
    const { t } = useLingui();
    const { client } = requestManager.graphQLClient;
    const { data, error, loading, refetch } = useQuery<GetAdaptiveSettingsQuery>(GET_ADAPTIVE_SETTINGS, {
        client,
        fetchPolicy: 'cache-and-network',
    });
    const [setEnabled, enabledResult] = useMutation<SetAdaptiveEnabledMutation, SetAdaptiveEnabledMutationVariables>(
        SET_ADAPTIVE_ENABLED,
        { client },
    );
    const [setSource, sourceResult] = useMutation<SetAdaptiveSourceMutation, SetAdaptiveSourceMutationVariables>(
        SET_ADAPTIVE_SOURCE,
        { client },
    );
    const settings = data?.adaptiveUpdateSettings;
    const saving = enabledResult.loading || sourceResult.loading;
    const save = async (operation: () => Promise<unknown>) => {
        try {
            await operation();
            await refetch();
        } catch (e) {
            makeToast(t`Failed to save changes`, 'error', getErrorMessage(e));
        }
    };
    if (error) {
        return (
            <Alert severity="warning">{t`Adaptive update settings could not be loaded. A matching server version is required.`}</Alert>
        );
    }
    if (!settings) {
        return (
            <ListItem>
                <ListItemText primary={loading ? t`Loading adaptive updates…` : t`Adaptive updates unavailable`} />
            </ListItem>
        );
    }
    const maxDays = settings.maxBackoffDays;
    return (
        <Box>
            <ListItem>
                <ListItemText
                    primary={t`Adaptive library updates`}
                    secondary={t`Learn release patterns to reduce automatic source requests. Manual refresh still checks immediately.`}
                />
                <Switch
                    checked={settings.enabled}
                    disabled={!settings.canManage || saving}
                    onChange={(e) => {
                        void save(() => setEnabled({ variables: { enabled: e.target.checked } }));
                    }}
                    slotProps={{ input: { 'aria-label': t`Adaptive library updates` } }}
                />
            </ListItem>
            <Typography
                variant="body2"
                color="text.secondary"
                sx={{ px: 2, pb: 1 }}
            >{t`Normal updates continue until four reliable release observations. Guard checks detect early releases; hiatus backoff is limited to ${maxDays} days. Your automatic update interval and exclusions still apply.`}</Typography>
            {!settings.canManage && (
                <Typography
                    variant="body2"
                    sx={{ px: 2 }}
                >{t`An administrator controls these shared settings.`}</Typography>
            )}
            {settings.canManage &&
                settings.sources.map((source) => (
                    <ListItem key={`${source.sourceId}:${source.providerPackage}`} sx={{ pl: 4 }}>
                        <ListItemText
                            primary={source.name}
                            secondary={
                                source.installed
                                    ? t`Use adaptive updates for this source`
                                    : t`Not installed. Preference retained for reinstall.`
                            }
                        />
                        <Switch
                            checked={source.enabled}
                            disabled={!settings.enabled || !source.installed || saving}
                            onChange={(e) => {
                                void save(() =>
                                    setSource({ variables: { sourceId: source.sourceId, enabled: e.target.checked } }),
                                );
                            }}
                            slotProps={{ input: { 'aria-label': t`Adaptive updates for ${source.name}` } }}
                        />
                    </ListItem>
                ))}
        </Box>
    );
};
