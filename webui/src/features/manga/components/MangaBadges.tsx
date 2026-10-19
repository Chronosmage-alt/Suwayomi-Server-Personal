/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import { styled } from '@mui/material/styles';
import Button from '@mui/material/Button';
import Typography from '@mui/material/Typography';
import { useLingui } from '@lingui/react/macro';
import type { MangaCardMode } from '@/features/manga/Manga.types.ts';
import { MediaQuery } from '@/base/utils/MediaQuery.tsx';
import { useMetadataServerSettings } from '@/features/settings/services/ServerSettingsMetadata.ts';
import { MUIUtil } from '@/lib/mui/MUI.util.ts';
import MenuBook from '@mui/icons-material/MenuBook';
import { useKomgaSummary } from '@/features/komga/KomgaLibrary.tsx';
import { CustomTooltip } from '@/base/components/CustomTooltip.tsx';

const BadgeContainer = styled('div')(({ theme }) => ({
    display: 'flex',
    height: 'fit-content',
    borderRadius: theme.shape.borderRadius,
    overflow: 'hidden',
}));

const Badge = styled(Typography)(({ theme }) => ({
    color: theme.palette.primary.contrastText,
    paddingInline: theme.spacing(0.3),
}));

export const MangaBadges = ({
    inLibraryIndicator,
    updateLibraryState,
    isInLibrary,
    unread,
    downloadCount,
    mode,
    mangaId,
}: {
    inLibraryIndicator?: boolean;
    updateLibraryState: () => void;
    isInLibrary: boolean;
    unread?: number;
    downloadCount?: number;
    mode: MangaCardMode;
    mangaId?: number;
}) => {
    const { t } = useLingui();
    const komga = useKomgaSummary(mangaId);

    const isTouchDevice = MediaQuery.useIsTouchDevice();

    const {
        settings: { showUnreadBadge, showDownloadBadge },
    } = useMetadataServerSettings();

    return (
        <BadgeContainer
            style={
                mode === 'default' && (komga?.bookCount != null || komga?.code === 'REVIEW')
                    ? { flexWrap: 'wrap', minWidth: 0 }
                    : undefined
            }
        >
            {mode === 'default' && komga?.code === 'REVIEW' && (
                <CustomTooltip
                    title={t`Komga link requires review. Open the manga's Komga section to confirm or correct it.`}
                >
                    <Badge
                        aria-label={t`Komga link requires review`}
                        sx={{ backgroundColor: '#1565c0', color: '#fff' }}
                    >
                        ?
                    </Badge>
                </CustomTooltip>
            )}
            {mode === 'default' && komga?.bookCount != null && (
                <CustomTooltip
                    title={t`Komga: ${komga.bookCount} stored books (not verified chapters). ${komga.stale ? 'Cached; information is stale.' : ''} ${komga.provenance === 'AUTOMATIC' ? 'Automatically linked.' : 'Manually confirmed.'}`}
                >
                    <Badge
                        aria-label={t`Komga: ${komga.bookCount} stored books${komga.stale ? ' (stale)' : ''}`}
                        sx={{
                            backgroundColor: '#1565c0',
                            color: '#fff',
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: 0.25,
                            whiteSpace: 'nowrap',
                            fontSize: { xs: '0.75rem', sm: '0.875rem' },
                        }}
                    >
                        <MenuBook sx={{ fontSize: '0.8em' }} />
                        {komga.bookCount > 9999
                            ? new Intl.NumberFormat(undefined, {
                                  notation: 'compact',
                                  maximumFractionDigits: 1,
                              }).format(komga.bookCount)
                            : komga.bookCount}
                        {komga.stale ? '*' : ''}
                    </Badge>
                </CustomTooltip>
            )}
            {!isTouchDevice && inLibraryIndicator && mode === 'source' && (
                <Button
                    className="source-manga-library-state-button"
                    component="div"
                    variant="contained"
                    size="small"
                    {...MUIUtil.preventRippleProp()}
                    onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        updateLibraryState();
                    }}
                    sx={{
                        display: 'none',
                    }}
                    color={isInLibrary ? 'error' : 'primary'}
                >
                    {isInLibrary ? t`Remove from the library` : t`Add To Library`}
                </Button>
            )}
            {inLibraryIndicator && isInLibrary && (
                <Typography
                    className="source-manga-library-state-indicator"
                    sx={{ backgroundColor: 'primary.dark', color: 'primary.contrastText', p: 0.3 }}
                >
                    {t`In Library`}
                </Typography>
            )}
            {((showUnreadBadge && mode === 'default') || mode === 'duplicate') && (unread ?? 0) > 0 && (
                <Badge sx={{ backgroundColor: 'primary.main', color: 'primary.contrastText' }}>{unread}</Badge>
            )}
            {((showDownloadBadge && mode === 'default') || mode === 'duplicate') && (downloadCount ?? 0) > 0 && (
                <Badge
                    sx={{
                        backgroundColor: 'secondary.main',
                        color: 'secondary.contrastText',
                    }}
                >
                    {downloadCount}
                </Badge>
            )}
        </BadgeContainer>
    );
};
