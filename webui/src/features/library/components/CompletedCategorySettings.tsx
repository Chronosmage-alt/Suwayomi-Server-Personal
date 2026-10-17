/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useEffect, useRef, useState } from 'react';
import { useQuery, useMutation, useLazyQuery } from '@apollo/client/react';
import { useLingui } from '@lingui/react/macro';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import {
    COMPLETED_CATEGORY_SETTINGS,
    SAVE_COMPLETED_CATEGORY_SETTINGS,
    COMPLETED_CATEGORY_PREVIEW,
    ORGANISE_COMPLETED_MANGA,
} from '@/lib/graphql/completed/CompletedCategories.ts';
import type {
    CompletedCategorySettingsQuery,
    SaveCompletedCategorySettingsMutation,
    CompletedCategoryPreviewQuery,
    OrganiseCompletedMangaMutation,
} from '@/lib/graphql/generated/graphql.ts';

export const CompletedCategorySettings = ({ categoryRevision = '' }: { categoryRevision?: string }) => {
    const { t } = useLingui();
    const { client } = requestManager.graphQLClient;
    const { data, error, refetch } = useQuery<CompletedCategorySettingsQuery>(COMPLETED_CATEGORY_SETTINGS, {
        client,
        fetchPolicy: 'cache-and-network',
    });
    const [save, saveState] = useMutation<SaveCompletedCategorySettingsMutation>(SAVE_COMPLETED_CATEGORY_SETTINGS, {
        client,
    });
    const [preview, previewState] = useLazyQuery<CompletedCategoryPreviewQuery>(COMPLETED_CATEGORY_PREVIEW, {
        client,
        fetchPolicy: 'network-only',
    });
    const [organise, organiseState] = useMutation<OrganiseCompletedMangaMutation>(ORGANISE_COMPLETED_MANGA, { client });
    const [open, setOpen] = useState(false);
    const [message, setMessage] = useState<string | null>(null);
    const [failure, setFailure] = useState(false);
    const config = data?.completedCategorySettings;
    const previousRevision = useRef(categoryRevision);
    useEffect(() => {
        if (previousRevision.current !== categoryRevision) {
            previousRevision.current = categoryRevision;
            refetch().catch(() => {});
        }
    }, [categoryRevision, refetch]);
    useEffect(() => {
        const refresh = () => {
            refetch().catch(() => {});
        };
        window.addEventListener('focus', refresh);
        return () => window.removeEventListener('focus', refresh);
    }, [refetch]);
    const run = async (operation: () => Promise<unknown>) => {
        setMessage(null);
        setFailure(false);
        try {
            await operation();
        } catch {
            setFailure(true);
            await refetch().catch(() => {});
        }
    };
    if (error) {
        return <Alert severity="warning">{t`Completed category settings could not be loaded.`}</Alert>;
    }
    if (!config) {
        return null;
    }
    const valid = config.categories.some(({ id }) => id === config.categoryId);
    const busy = saveState.loading || previewState.loading || organiseState.loading;
    const change = (enabled: boolean, categoryId: number | null) =>
        run(async () => {
            setOpen(false);
            await save({ variables: { enabled, categoryId } });
            await refetch();
        });
    const counts = previewState.data?.completedCategoryPreview;
    const additions = counts?.toAdd ?? 0;
    const present = counts?.alreadyPresent ?? 0;
    const skipped = counts?.skipped ?? 0;
    return (
        <Stack spacing={1} sx={{ px: 2, py: 1 }}>
            <Typography variant="subtitle1">{t`Completed category`}</Typography>
            <FormControlLabel
                label={t`Automatically categorise completed manga`}
                control={
                    <Switch
                        checked={config.enabled}
                        disabled={busy || (!valid && !config.enabled)}
                        onChange={(_, checked) => {
                            void change(checked, config.categoryId ?? null);
                        }}
                    />
                }
            />
            <TextField
                select
                size="small"
                label={t`Destination category`}
                value={valid ? config.categoryId : ''}
                disabled={busy}
                onChange={(event) => {
                    void change(config.enabled, event.target.value === '' ? null : Number(event.target.value));
                }}
            >
                <MenuItem value="" disabled={config.enabled}>{t`Select a category`}</MenuItem>
                {config.categories.map(({ id, name }) => (
                    <MenuItem key={id} value={id}>
                        {name}
                    </MenuItem>
                ))}
            </TextField>
            {config.destinationMissing && (
                <Alert severity="warning">{t`The destination category is unavailable. Automatic categorisation is inactive. Select another category.`}</Alert>
            )}
            <Typography variant="body2">{t`After a successful metadata refresh reports Completed, this adds library manga to the selected category once and keeps all other categories. It never removes titles when their status changes.`}</Typography>
            <Typography variant="body2">{t`Removing a title manually from the destination category prevents it from being automatically re-added or added by Organise. Turning the rule off and on does not reset this. Selecting a different destination allows one addition there.`}</Typography>
            <Typography
                variant="body2"
                color="text.secondary"
            >{t`This is independent of Exclude Completed. It does not refresh sources, change reading progress or manage downloads. Existing category update and download rules still apply.`}</Typography>
            {failure && (
                <Alert severity="error">{t`The operation failed or the destination changed. Review the category and try again.`}</Alert>
            )}
            {message && <Alert severity="success">{message}</Alert>}
            <Button
                disabled={!valid || busy}
                onClick={() => {
                    void run(async () => {
                        await preview({ variables: { categoryId: config.categoryId } });
                        setOpen(true);
                    });
                }}
            >{t`Organise existing completed manga`}</Button>
            <Dialog
                open={open}
                onClose={() => {
                    if (!busy) {
                        setOpen(false);
                    }
                }}
            >
                <DialogTitle>{t`Organise existing completed manga`}</DialogTitle>
                <DialogContent>
                    <Stack spacing={1}>
                        <Typography>{t`${additions} titles will be added. ${present} are already present. ${skipped} will be skipped because they were previously handled or manually removed.`}</Typography>
                        <Typography>{t`Uses stored library statuses only, without contacting sources. Existing categories are preserved. Counts are checked again when applying.`}</Typography>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button disabled={busy} onClick={() => setOpen(false)}>{t`Cancel`}</Button>
                    <Button
                        disabled={busy || !counts || counts.categoryId !== config.categoryId}
                        onClick={() => {
                            void run(async () => {
                                const result = await organise({ variables: { categoryId: config.categoryId } });
                                const added = result.data?.organiseCompletedManga.toAdd ?? 0;
                                setMessage(t`Added ${added} titles to the destination category.`);
                                setOpen(false);
                                await refetch();
                            });
                        }}
                    >{t`Apply organisation`}</Button>
                </DialogActions>
            </Dialog>
        </Stack>
    );
};
