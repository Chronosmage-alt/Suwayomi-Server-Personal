/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useLingui } from '@lingui/react/macro';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import IconButton from '@mui/material/IconButton';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import { MangaJobStatus } from '@/lib/graphql/generated/graphql-base.types.ts';

const SkippedUpdates = () => {
    const { t } = useLingui();
    const { data, loading, error } = requestManager.useGetGlobalUpdateSummary({
        fetchPolicy: 'cache-and-network',
        pollInterval: 2000,
    });
    if (error) {
        return <Alert severity="warning">{t`Skipped updates could not be loaded.`}</Alert>;
    }
    if (loading && !data) {
        return <Typography>{t`Loading skipped updates…`}</Typography>;
    }
    const jobs = data?.libraryUpdateStatus.mangaUpdates.filter((job) => job.status === MangaJobStatus.Skipped) ?? [];
    if (!jobs.length) {
        return <Typography>{t`No skipped titles in the current update.`}</Typography>;
    }
    return (
        <List>
            {jobs.map((job) => {
                const reason =
                    {
                        ADAPTIVE_NOT_DUE: t`Not due for an adaptive check.`,
                        ALREADY_CHECKED_OR_NOT_DUE: t`Already refreshed after this request was queued, or no longer due.`,
                        EXCLUDED_BY_UPDATE_FILTERS: t`Excluded by existing library update settings or the source update strategy.`,
                    }[job.skipReason ?? ''] ?? t`Skipped by the updater.`;
                return (
                    <ListItem key={job.manga.id} disableGutters>
                        <ListItemText
                            primary={
                                <Button component={Link} to={`/manga/${job.manga.id}`}>
                                    {job.manga.title}
                                </Button>
                            }
                            secondary={
                                job.nextCheckAt && Number(job.nextCheckAt) > 0
                                    ? `${reason} ${t`Next eligible check: ${new Date(Number(job.nextCheckAt)).toLocaleString()}`}`
                                    : reason
                            }
                        />
                    </ListItem>
                );
            })}
        </List>
    );
};

export const SkippedUpdateDialog = () => {
    const { t } = useLingui();
    const [open, setOpen] = useState(false);
    return (
        <>
            <IconButton color="inherit" aria-label={t`View skipped updates`} onClick={() => setOpen(true)}>
                <InfoOutlinedIcon />
            </IconButton>
            <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="sm">
                <DialogTitle>{t`Skipped library updates`}</DialogTitle>
                <DialogContent>
                    <Typography variant="body2">{t`Eligibility dates are followed by the next automatic library update. Exclusions, queues and source cooldowns may delay checks. Open a title for its schedule or a manual refresh.`}</Typography>
                    {open && <SkippedUpdates />}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setOpen(false)}>{t`Close`}</Button>
                </DialogActions>
            </Dialog>
        </>
    );
};
