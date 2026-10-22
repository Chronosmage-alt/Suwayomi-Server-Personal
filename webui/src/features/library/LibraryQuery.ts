/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

import type { LibrarySortMode } from '@/features/library/Library.types.ts';

export const getLibraryQueryVariables = (sortBy: NullAndUndefined<LibrarySortMode>, showContinueReading = true) => ({
    includeContinueReading: showContinueReading,
    includeLastRead: sortBy === 'lastRead',
    includeLatestFetched: sortBy === 'latestFetchedChapter',
    includeLatestUploaded: sortBy === 'latestUploadedChapter',
});
