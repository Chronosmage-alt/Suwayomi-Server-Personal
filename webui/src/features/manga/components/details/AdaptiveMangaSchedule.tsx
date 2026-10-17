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
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import {
    GET_ADAPTIVE_MANGA,
    SET_ADAPTIVE_MANGA_MODE,
    RESET_ADAPTIVE_MANGA,
} from '@/lib/graphql/adaptive/AdaptiveUpdates.ts';
import type {
    GetAdaptiveMangaQuery,
    GetAdaptiveMangaQueryVariables,
    SetAdaptiveMangaModeMutation,
    SetAdaptiveMangaModeMutationVariables,
    ResetAdaptiveMangaMutation,
    ResetAdaptiveMangaMutationVariables,
} from '@/lib/graphql/generated/graphql.ts';
import { AdaptiveUpdateMode } from '@/lib/graphql/generated/graphql-base.types.ts';
import { makeToast } from '@/base/utils/Toast.ts';
import { getErrorMessage } from '@/lib/HelperFunctions.ts';

const date = (value: string) => new Date(Number(value)).toLocaleString();

export const AdaptiveMangaSchedule = ({ mangaId }: { mangaId: number }) => {
    const { t } = useLingui();
    const { client } = requestManager.graphQLClient;
    const { data, error, refetch } = useQuery<GetAdaptiveMangaQuery, GetAdaptiveMangaQueryVariables>(
        GET_ADAPTIVE_MANGA,
        { client, variables: { mangaId }, fetchPolicy: 'cache-and-network', pollInterval: 30000 },
    );
    const [setMode, modeResult] = useMutation<SetAdaptiveMangaModeMutation, SetAdaptiveMangaModeMutationVariables>(
        SET_ADAPTIVE_MANGA_MODE,
        { client },
    );
    const [reset, resetResult] = useMutation<ResetAdaptiveMangaMutation, ResetAdaptiveMangaMutationVariables>(
        RESET_ADAPTIVE_MANGA,
        { client },
    );
    const schedule = data?.adaptiveMangaSchedule;
    const saving = modeResult.loading || resetResult.loading;
    const save = async (operation: () => Promise<unknown>) => {
        try {
            await operation();
            await refetch();
        } catch (e) {
            makeToast(t`Failed to save changes`, 'error', getErrorMessage(e));
        }
    };
    if (error) {
        return <Alert severity="warning">{t`Adaptive scheduling information could not be loaded.`}</Alert>;
    }
    if (!schedule) {
        return null;
    }
    const count = schedule.observations;
    const intervalDays = schedule.intervalDays?.toLocaleString(undefined, { maximumFractionDigits: 1 });
    const provisionalDays = schedule.provisionalIntervalDays?.toLocaleString(undefined, { maximumFractionDigits: 1 });
    const confirmations = schedule.historicalConfirmations;
    const diagnostic = schedule.historicalDiagnostic;
    const historicalExplanation = diagnostic
        ? {
              PROVISIONAL: t`Reliable historical evidence supports the provisional schedule. Two new releases must confirm it before checks are reduced.`,
              ACTIVE_SCHEDULE: t`A release schedule is active. Historical hints are no longer needed while it remains active.`,
              MISSING_DATES: t`Historical learning: recent chapters have missing dates.`,
              UNKNOWN_DATES: t`Historical learning: the origin of recent chapter dates is unknown. Unchanged or older dates without recorded proof cannot establish their origin.`,
              FALLBACK_DATES: t`Historical learning: recent dates include server-generated fallback timestamps.`,
              INVALID_NUMBERS: t`Historical learning: chapter numbers are missing or invalid.`,
              INVALID_DATES: t`Historical learning: recent chapter dates are in the future.`,
              DUPLICATE_DATES: t`Historical learning: duplicate chapter editions have conflicting dates.`,
              REVERSED_DATES: t`Historical learning: dates run backwards as chapter numbers increase.`,
              BULK_HISTORY: t`Historical learning: recent dates suggest an ambiguous bulk upload.`,
              INSUFFICIENT_EVENTS: t`Historical learning: fewer than six reliable release events are available.`,
              UNSUPPORTED_INTERVAL: t`Historical learning: the estimated interval is outside the supported range of more than 2 days and at most 60 days.`,
              INCONSISTENT_INTERVALS: t`Historical learning: recent release intervals are inconsistent.`,
              STALE_HISTORY: t`Historical learning: the latest historical release event is too old.`,
              SUPPRESSED: t`Historical learning: reuse of existing history is suppressed after a reset or chapter restoration. Newer history is required.`,
              WAITING_FOR_NEW_HISTORY: t`Historical learning: unchanged history cannot be retried. At least six entirely newer reliable release events are required.`,
              AMBIGUOUS_ADDITIONS: t`Historical learning: chapter additions cannot safely be treated as new releases. Normal learning continues.`,
              CONTRADICTED: t`Historical learning: new evidence contradicted the provisional prediction. Normal learning continues.`,
              EXPIRED: t`Historical learning: the provisional prediction or learning state expired. Newer reliable history is required.`,
          }[diagnostic.reason]
        : t`Historical learning has not been assessed since this update.`;
    const explanation = {
        MASTER_DISABLED: t`Adaptive updates are disabled globally. Normal automatic updates apply.`,
        TITLE_NORMAL: t`This title uses normal automatic updates.`,
        SOURCE_DISABLED: t`Adaptive updates are disabled for this source.`,
        LEARNING: provisionalDays
            ? t`Provisional release interval: about ${provisionalDays} days. ${confirmations} of 2 new releases confirmed; normal automatic updates continue.`
            : t`Learning: ${count} recent qualifying releases. Four consistent observations are required; normal automatic updates continue.`,
        FREQUENT_RELEASE: t`Releases are frequent. Normal automatic updates continue.`,
        SCHEDULED: t`Learned release interval: about ${intervalDays} days. Checks between releases are reduced.`,
        BACKOFF: t`Expected release missed. Checks are backed off, with a maximum interval of 14 days.`,
    }[schedule.reason];
    return (
        <Stack spacing={1} sx={{ px: 1 }}>
            <Typography variant="subtitle2">{t`Automatic update schedule`}</Typography>
            <TextField
                select
                size="small"
                label={t`Update behaviour`}
                value={schedule.mode}
                disabled={!schedule.canManage || saving}
                onChange={(e) => {
                    void save(() => setMode({ variables: { mangaId, mode: e.target.value as AdaptiveUpdateMode } }));
                }}
            >
                <MenuItem value={AdaptiveUpdateMode.FollowSource}>{t`Follow source setting`}</MenuItem>
                <MenuItem value={AdaptiveUpdateMode.Smart}>{t`Adaptive updates`}</MenuItem>
                <MenuItem value={AdaptiveUpdateMode.Normal}>{t`Normal updates`}</MenuItem>
            </TextField>
            <Typography variant="body2" color="text.secondary">
                {explanation}
            </Typography>
            <Typography variant="caption">{t`Recent qualifying release observations: ${count}.`}</Typography>
            <Typography variant="body2" color="text.secondary">
                {historicalExplanation}
            </Typography>
            {diagnostic?.assessedAt && (
                <Typography variant="caption">
                    {t`Last historical assessment: ${date(diagnostic.assessedAt)}.`}{' '}
                    {t`Inspected chapter numbers: ${diagnostic.inspectedNumbers}; grouped release events: ${diagnostic.events}.`}{' '}
                    {t`Dates: ${diagnostic.eligibleDates} passing origin/time checks, ${diagnostic.unknownDates} unknown, ${diagnostic.fallbackDates} fallback, ${diagnostic.missingDates} missing.`}
                </Typography>
            )}
            <Typography variant="caption" color="text.secondary">
                {t`Extension-reported dates may be approximate or reused; they are not proof of a freshly parsed publication timestamp. Historical rejection does not stop normal four-observation learning.`}
            </Typography>
            {schedule.nextEligibleAt && Number(schedule.nextEligibleAt) > Date.now() && (
                <Typography variant="body2">{t`Automatic checks skipped until ${date(schedule.nextEligibleAt)}.`}</Typography>
            )}
            <Typography variant="body2">
                {schedule.nextAutomaticCheckAt
                    ? t`Next automatic opportunity: ${date(schedule.nextAutomaticCheckAt)}`
                    : t`Automatic library updates are disabled.`}
            </Typography>
            {schedule.lastCheckAt && (
                <Typography variant="caption">{t`Last successful check: ${date(schedule.lastCheckAt)}`}</Typography>
            )}
            {schedule.lastDiscoveryAt && (
                <Typography variant="caption">{t`Last qualifying release discovered: ${date(schedule.lastDiscoveryAt)}`}</Typography>
            )}
            {schedule.lastReleaseAt && (
                <Typography variant="caption">
                    {schedule.usesPublicationDate
                        ? t`Source publication date: ${date(schedule.lastReleaseAt)}`
                        : t`Release timing uses discovery time; no reliable publication date was available.`}
                </Typography>
            )}
            <Typography
                variant="caption"
                color="text.secondary"
            >{t`Guard checks allow early releases and older chapter additions. Exclusions, queueing and source cooldowns can delay checks. Manual refresh bypasses this schedule and retains source protections.`}</Typography>
            {schedule.canManage && (
                <Button
                    size="small"
                    disabled={saving || (!schedule.observations && !schedule.provisionalIntervalDays)}
                    onClick={() => {
                        void save(() => reset({ variables: { mangaId } }));
                    }}
                >{t`Reset release learning`}</Button>
            )}
            {!schedule.canManage && (
                <Typography variant="caption">{t`An administrator controls this shared title setting.`}</Typography>
            )}
        </Stack>
    );
};
