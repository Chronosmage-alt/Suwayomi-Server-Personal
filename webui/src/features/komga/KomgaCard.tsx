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
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import Accordion from '@mui/material/Accordion';
import AccordionSummary from '@mui/material/AccordionSummary';
import AccordionDetails from '@mui/material/AccordionDetails';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { useKomgaSettings } from '@/features/komga/KomgaSettings.tsx';
import { useDebounce } from '@/base/hooks/useDebounce.ts';
import { requestManager } from '@/lib/requests/RequestManager.ts';
import { KOMGA_MANGA, KOMGA_SEARCH, LINK_KOMGA, UNLINK_KOMGA, REFRESH_KOMGA } from '@/lib/graphql/komga/Komga.ts';
import type {
    KomgaMangaQuery,
    KomgaSearchQuery,
    LinkKomgaMutation,
    UnlinkKomgaMutation,
    RefreshKomgaMutation,
} from '@/lib/graphql/generated/graphql.ts';
type KomgaCardProps = { mangaId: number; title: string };
const KomgaCardContent = ({ mangaId, title }: KomgaCardProps) => {
    const { t } = useLingui();
    const { client } = requestManager.graphQLClient;
    const settings = useKomgaSettings();
    const enabled = !!settings.data?.komgaSettings.enabled;
    const info = useQuery<KomgaMangaQuery>(KOMGA_MANGA, {
        client,
        variables: { mangaId },
        skip: !enabled,
        fetchPolicy: 'cache-and-network',
    });
    const [open, setOpen] = useState(false);
    const [search, setSearch] = useState(title);
    const [page, setPage] = useState(0);
    const [bookPage, setBookPage] = useState(0);
    const [message, setMessage] = useState('');
    const debounced = useDebounce(search.trim(), 350);
    const results = useQuery<KomgaSearchQuery>(KOMGA_SEARCH, {
        client,
        variables: { query: debounced, page },
        skip: !enabled || !open || debounced.length < 2,
        fetchPolicy: 'network-only',
    });
    const [link, linking] = useMutation<LinkKomgaMutation>(LINK_KOMGA, { client });
    const [unlink, unlinking] = useMutation<UnlinkKomgaMutation>(UNLINK_KOMGA, { client });
    const [refresh, refreshing] = useMutation<RefreshKomgaMutation>(REFRESH_KOMGA, { client });
    const data = info.data?.komgaManga;
    useEffect(() => {
        setBookPage(0);
    }, [data?.seriesId]);
    if (!enabled) {
        return null;
    }
    const missingLabel = data?.missingCount ?? t`uncertain`;
    const busy = linking.loading || unlinking.loading || refreshing.loading;
    const store = (value: KomgaMangaQuery['komgaManga'] | null | undefined) => {
        if (value) {
            client.writeQuery({ query: KOMGA_MANGA, variables: { mangaId }, data: { komgaManga: value } });
            setMessage(value.code === 'READY' || value.code === 'UNLINKED' ? '' : value.message);
        }
    };
    const failure = () => setMessage(t`Komga request failed. Try again or check the connection settings.`);
    const readLabel = (read: boolean | null | undefined) => {
        if (read == null) {
            return '?';
        }
        return read ? t`yes` : t`no`;
    };
    const linkLabel =
        data?.provenance === 'AUTOMATIC'
            ? t`Automatically linked — correct or unlink if needed`
            : t`Manually confirmed link`;
    const list = (values: string[]) => (values.length ? values.join(', ') : t`None`);
    return (
        <Stack
            id="komga"
            spacing={1}
            sx={{ p: 2, minWidth: 0, overflowWrap: 'anywhere', border: 1, borderColor: 'divider', borderRadius: 1 }}
        >
            <Typography variant="h6">Komga</Typography>
            {data?.linked && (
                <Typography variant="caption">
                    {data.code === 'CONNECTION_CHANGED' ? t`Link requires review` : linkLabel}
                </Typography>
            )}
            {data?.provenance === 'OPTED_OUT' && (
                <Typography variant="caption">{t`Automatic linking is permanently suppressed. Manually link a series to allow linking again.`}</Typography>
            )}
            {data?.linked && data.provenance === 'AUTOMATIC' && data.seriesId && (
                <Button
                    disabled={linking.loading}
                    onClick={() => {
                        link({ variables: { mangaId, seriesId: data.seriesId! } })
                            .then((result) => store(result.data?.linkKomgaSeries))
                            .catch(failure);
                    }}
                >{t`Confirm automatic link`}</Button>
            )}
            {info.loading && !data && <Typography>{t`Loading Komga inventory…`}</Typography>}
            {info.error && <Alert severity="warning">{t`Komga inventory could not be loaded.`}</Alert>}
            {data && (
                <>
                    {data.title && <Typography variant="subtitle1">{data.title}</Typography>}
                    <Alert severity={data.code === 'READY' && data.missingCount !== null ? 'info' : 'warning'}>
                        {data.message}
                    </Alert>
                    {data.fetchedAt && (
                        <>
                            <Typography>{t`Suwayomi downloaded entries: ${data.suwayomiDownloaded} / ${data.sourceEntries}`}</Typography>
                            <Typography>{t`Komga books: ${data.bookCount ?? '?'}. Read: ${data.readBooks ?? '?'}, unread: ${data.unreadBooks ?? '?'}, in progress: ${data.inProgressBooks ?? '?'}.`}</Typography>
                            <Typography>{t`Confirmed distinct chapter numbers: ${data.confirmedNumbers.length}. Missing: ${missingLabel}.`}</Typography>
                            <Typography variant="caption">
                                {t`Last fetched: ${new Date(Number(data.fetchedAt)).toLocaleString()}`}
                                {data.stale ? t` (cached; Komga is unavailable)` : ''}
                            </Typography>
                            {data.lastProgressAt && (
                                <Typography variant="caption">{t`Last Komga progress change: ${data.lastProgressAt}`}</Typography>
                            )}
                            <Accordion disableGutters>
                                <AccordionSummary
                                    expandIcon={<ExpandMoreIcon />}
                                >{t`Inventory comparison and book progress`}</AccordionSummary>
                                <AccordionDetails>
                                    <Typography>{t`Confirmed chapter numbers: ${list(data.confirmedNumbers)}`}</Typography>
                                    <Typography>{t`Unverified number hints (may be volumes): ${list(data.numberHints)}`}</Typography>
                                    <Typography>{t`Source chapter numbers not matched: ${list(data.unmatchedSourceNumbers)}`}</Typography>
                                    <Typography>{t`Extra number hints outside the stored source list: ${list(data.extraNumberHints)}`}</Typography>
                                    <Typography>{t`Confirmed in Komga but not flagged downloaded: ${list(data.confirmedButNotDownloaded)}`}</Typography>
                                    <Typography>{t`Flagged downloaded but not confirmed in Komga: ${list(data.downloadedButNotMatched)}`}</Typography>
                                    <Typography>{t`Ambiguous books: ${data.ambiguousBooks}. Unnumbered source entries: ${data.unknownSourceEntries}. Duplicate source entries: ${data.duplicateSourceEntries}.`}</Typography>
                                    {!data.inventoryComplete && (
                                        <Alert severity="warning">{t`Inventory is incomplete or changed during pagination. Missing counts are not definitive.`}</Alert>
                                    )}
                                    <Typography variant="caption">{t`Compared with the stored source list; this does not refresh sources or verify files on disk. Reading counts are books, not assumed chapter totals.`}</Typography>
                                    <List dense>
                                        {data.books.slice(bookPage * 50, (bookPage + 1) * 50).map((book) => (
                                            <ListItem key={book.id}>
                                                <ListItemText
                                                    primary={book.title}
                                                    secondary={t`Number hint: ${book.numberHint ?? '?'}. Read: ${readLabel(book.read)}. Page: ${book.page ?? '-'} / ${book.pages ?? '?'}.`}
                                                />
                                            </ListItem>
                                        ))}
                                    </List>
                                    <Button
                                        disabled={bookPage === 0}
                                        onClick={() => setBookPage(bookPage - 1)}
                                    >{t`Previous`}</Button>
                                    <Button
                                        disabled={(bookPage + 1) * 50 >= data.books.length}
                                        onClick={() => setBookPage(bookPage + 1)}
                                    >{t`Next`}</Button>
                                </AccordionDetails>
                            </Accordion>
                        </>
                    )}
                </>
            )}
            <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 1 }}>
                <Button
                    disabled={busy}
                    onClick={() => {
                        setSearch(title);
                        setPage(0);
                        setOpen(true);
                    }}
                >
                    {data?.linked ? t`Correct Komga link` : t`Link Komga series`}
                </Button>
                {data?.linked && (
                    <>
                        <Button
                            disabled={busy}
                            onClick={() => {
                                void refresh({ variables: { mangaId } })
                                    .then((result) => {
                                        store(result.data?.refreshKomgaManga);
                                        setBookPage(0);
                                    })
                                    .catch(failure);
                            }}
                        >{t`Refresh Komga`}</Button>
                        <Button
                            disabled={busy}
                            onClick={() => {
                                void unlink({ variables: { mangaId } })
                                    .then((result) => store(result.data?.unlinkKomgaSeries))
                                    .catch(failure);
                            }}
                        >{t`Unlink`}</Button>
                    </>
                )}
                {data?.openUrl && (
                    <Button
                        component="a"
                        href={data.openUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                    >{t`Open in Komga`}</Button>
                )}
            </Stack>
            {message && <Alert severity="warning">{message}</Alert>}
            <Dialog open={open} onClose={() => !busy && setOpen(false)} fullWidth maxWidth="sm">
                <DialogTitle>{t`Link a Komga series`}</DialogTitle>
                <DialogContent>
                    <TextField
                        fullWidth
                        label={t`Search Komga titles or alternative titles`}
                        value={search}
                        onChange={(e) => {
                            setSearch(e.target.value);
                            setPage(0);
                        }}
                        sx={{ mt: 1 }}
                    />
                    <Typography variant="caption">{t`Suggestions are never linked automatically. Confirm the correct series.`}</Typography>
                    {(results.error || (results.data?.komgaSearch.code !== 'READY' && results.data)) && (
                        <Alert severity="warning">{results.data?.komgaSearch.message ?? t`Komga search failed.`}</Alert>
                    )}
                    {results.loading && <Typography>{t`Searching Komga…`}</Typography>}
                    <List>
                        {results.data?.komgaSearch.items.map((series) => (
                            <ListItem key={series.id} sx={{ flexWrap: 'wrap', gap: 1 }}>
                                <ListItemText
                                    primary={series.title}
                                    secondary={t`${series.alternateTitles.join(' · ')} (${series.bookCount ?? '?'} books)`}
                                />
                                <Button
                                    disabled={busy || results.loading}
                                    onClick={() => {
                                        void link({ variables: { mangaId, seriesId: series.id } })
                                            .then((result) => {
                                                const value = result.data?.linkKomgaSeries;
                                                store(value);
                                                if (value?.seriesId === series.id && value.linked) {
                                                    setBookPage(0);
                                                    setOpen(false);
                                                }
                                            })
                                            .catch(failure);
                                    }}
                                >{t`Confirm link`}</Button>
                            </ListItem>
                        ))}
                    </List>
                    {results.data?.komgaSearch.code === 'READY' &&
                        !results.loading &&
                        !results.data.komgaSearch.items.length && (
                            <Typography>{t`No matching series. Try another title.`}</Typography>
                        )}
                    <Button
                        disabled={page === 0 || results.loading}
                        onClick={() => setPage(page - 1)}
                    >{t`Previous`}</Button>
                    <Button
                        disabled={!results.data?.komgaSearch.hasNextPage || results.loading}
                        onClick={() => setPage(page + 1)}
                    >{t`Next`}</Button>
                </DialogContent>
                <DialogActions>
                    <Button disabled={busy} onClick={() => setOpen(false)}>{t`Close`}</Button>
                </DialogActions>
            </Dialog>
        </Stack>
    );
};
export const KomgaCard = (props: KomgaCardProps) => <KomgaCardContent key={props.mangaId} {...props} />;
